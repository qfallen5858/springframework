package com.qufan.framework.annotation;

import java.lang.annotation.*;

/**
 * Created by qufan
 * 2019/4/1 0001
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QfAutowired {
    String value() default "";
}
