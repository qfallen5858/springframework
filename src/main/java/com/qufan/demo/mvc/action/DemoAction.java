package com.qufan.demo.mvc.action;

import com.qufan.demo.service.IDemoService;
import com.qufan.framework.annotation.QfAutowired;
import com.qufan.framework.annotation.QfController;
import com.qufan.framework.annotation.QfRequestMapping;
import com.qufan.framework.annotation.QfRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by qufan
 * 2019/4/1 0001
 */
@QfController
@QfRequestMapping("/demo")
public class DemoAction {

    @QfAutowired
    private IDemoService demoService;

    @QfRequestMapping("/query.json")
    public void query(HttpServletRequest req, HttpServletResponse resp, @QfRequestParam("name") String name){
        String result = demoService.get(name);
        try {
            resp.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @QfRequestMapping("/add.json")
    public void add(HttpServletRequest request, HttpServletResponse response, @QfRequestParam("a") Integer a, @QfRequestParam("b") Integer b){
        try {
            response.getWriter().write(a + "+" + b + "=" + (a+b));
        }catch (IOException e){
            e.printStackTrace();
        }
    }

}
