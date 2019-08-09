package xyz.suiwo.action.service.impl;

import xyz.suiwo.action.service.DemoService;
import xyz.suiwo.framework.annotation.SWService;

@SWService
public class DemoServiceImpl implements DemoService {

    public void get() {
        System.out.println("This is method get");
    }
}
