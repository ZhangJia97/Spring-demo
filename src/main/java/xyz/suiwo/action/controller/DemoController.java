package xyz.suiwo.action.controller;

import xyz.suiwo.action.service.DemoService;
import xyz.suiwo.framework.annotation.SWAutowried;
import xyz.suiwo.framework.annotation.SWController;
import xyz.suiwo.framework.annotation.SWRequestMapping;
import xyz.suiwo.framework.annotation.SWRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SWController
@SWRequestMapping("/get")
public class DemoController {

    @SWAutowried
    DemoService demoService;

    @SWRequestMapping("/method")
    public void get(HttpServletRequest request, HttpServletResponse response,
                    @SWRequestParam("name") String name){
        demoService.get();
        try {
            response.getWriter().write("my name is " + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
