package com.qufan.demo.service.impl;

import com.qufan.demo.service.IDemoService;
import com.qufan.framework.annotation.QfService;

/**
 * Created by qufan
 * 2019/4/1 0001
 */
@QfService
public class DemoService implements IDemoService {
    public String get(String name) {
        return "my name is " + name;
    }
}
