package com.qufan.framework.webmvc.servlet;

import com.qufan.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by qufan
 * 2019/4/1 0001
 */
public class QFDispatcherServlet extends HttpServlet {
    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

//    private Map<String, Method> handlerMapping = new HashMap<String, Method>();
    private List<Handler> handlerMapping = new ArrayList<Handler>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6.等待请求
        try{
            doDispatch(req, resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 Exception");
        }

    }

    private Handler getHandler(HttpServletRequest req)throws Exception{
        if(handlerMapping.isEmpty()){
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler: handlerMapping){
            try{
                Matcher matcher = handler.pattern.matcher(url);
                if(!matcher.matches()){
                    continue;
                }
                return handler;
            }catch (Exception e){
                throw e;
            }
        }
        return null;
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        try {
            Handler handler = getHandler(req);
            if(handler == null){
                resp.getWriter().write("404 Not found");
                return;
            }

            //获取方法的参数列表
            Class<?>[] paramsTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[paramsTypes.length];

            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param: params.entrySet()){
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");//////////////

                if(!handler.paramIndexMapping.containsKey(param.getKey())){
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());

                paramValues[index] = convert(paramsTypes[index], value);


            }
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
            handler.method.invoke(handler.controller, paramValues);
        }catch (Exception e){
            throw e;
        }
//        String url = req.getRequestURI();
//        String contextPath = req.getContextPath();
//        url = url.replace(contextPath, "").replaceAll("/+", "/");
    }

    private Object convert(Class<?> clazz, String value){
        String name = clazz.getName();
        if("java.lang.Integer".equals(name)){
            return Integer.parseInt(value);
        }
        if("java.lang.String".equals(name)){
            return value;
        }

        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //启动

        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描所有相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3.初始化所有相关的类
        doInstance();

        //4.自动注入
        doAutowired();

        //---------------spring初始化完成-----------

        //5.初始化HandlerMapping，属于springmvc
        initHandlerMapping();

        System.out.println("QF spring init completed");

        //IOC初始化

        //scan-package

    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry: ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(QfController.class)){
                continue;
            }

            String baseUrl = "";
            if(clazz.isAnnotationPresent(QfRequestMapping.class)){
                QfRequestMapping requestMapping = clazz.getAnnotation(QfRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method: clazz.getMethods()){
                if(!method.isAnnotationPresent(QfRequestMapping.class)){
                    continue;
                }

                QfRequestMapping requestMapping = method.getAnnotation(QfRequestMapping.class);
                String methodUrl = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");

//                handlerMapping.put(methodUrl, method);
                Pattern pattern = Pattern.compile(methodUrl);
                handlerMapping.add(new Handler(pattern, entry.getValue(), method));
                System.out.println("Mapping :" + methodUrl + ",method:" + method);
            }
        }

    }

    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }

        //循环IOC容器中所有类，然后对需要自动赋值的属性进行赋值
        for (Map.Entry<String, Object> entry: ioc.entrySet()){
            //依赖注入，不管是谁，强吻
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field: fields){
                if(!field.isAnnotationPresent(QfAutowired.class)){
                    continue;
                }

                QfAutowired autowired = field.getAnnotation(QfAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                //暴力访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                }catch (IllegalAccessException e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }

        try {
            for (String className: classNames){
                Class<?> clazz =  Class.forName(className);
                //不是所有的类都要实例化，只认加了注解的类
                if(clazz.isAnnotationPresent(QfController.class)){
                    //KEY默认是类名首字母小写
                    String beanName = lowerFirstCase(clazz.getName());
                    ioc.put(beanName, clazz.newInstance());
//                    ioc.put(clazz.)
                }else if(clazz.isAnnotationPresent(QfService.class)){

                    //2.如果自己定义了名字的话，优先使用自定义的名字

                    QfService service = clazz.getAnnotation(QfService.class);
                    String beanName = service.value();
                    //1.默认采用首字母小写
                    if("".equals(beanName.trim())){
                        beanName = lowerFirstCase(clazz.getName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //3.根据接口类型来赋值
                    for (Class<?> i: clazz.getInterfaces()){
                        ioc.put(i.getName(), instance);
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));

        File classDir = new File(url.getFile());
        for (File file: classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else{
                String className = scanPackage + "." + file.getName().replace(".class","");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is =  this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private class Handler{
        protected Object controller;    //保存方法对应的实例
        protected Method method;        //保存映射的方法
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;   //参数顺序

        protected Handler(Pattern pattern, Object controller, Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);

        }

        private void putParamIndexMapping(Method method){
            Annotation [][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i ++){
                for (Annotation a: pa[i]){
                    if(a instanceof QfRequestParam){
                        String paramName = ((QfRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            //提取方法中的request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for(int i = 0; i < paramsTypes.length; i ++){
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }
    }
}
