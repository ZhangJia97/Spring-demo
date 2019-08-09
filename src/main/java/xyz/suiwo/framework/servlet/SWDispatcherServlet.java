package xyz.suiwo.framework.servlet;

import xyz.suiwo.framework.annotation.SWAutowried;
import xyz.suiwo.framework.annotation.SWController;
import xyz.suiwo.framework.annotation.SWRequestMapping;
import xyz.suiwo.framework.annotation.SWService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class SWDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private Map<String, Method> handlerMapping = new HashMap<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req,resp);

        //注：当使用父类的doGet以及doPost可能会导致405错误
//        super.doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        doDispatch(req,resp);
//        doDispatch(req, resp);
    }

    @Override
    public void init(ServletConfig config)  {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描所有相关的类
        doScanner(properties.getProperty("scanPackage"));

        //3.初始化所有相关Class的实例，并且将其保存到IOC容器中
        doInstance();

        //4.自动化的依赖注入
        doAutowired();

        //5.初始化handlerMapping
        initHandlerMapping();

        System.out.println("初始化成功");
    }

    private void doLoadConfig(String location) {

        InputStream inputStream =null;
        try {
            inputStream = this.getClass().getClassLoader().getResourceAsStream(location);
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != inputStream) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //进行扫描
    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader()
                .getResource("/" + packageName.replaceAll("\\.", "/"));
        File classDir = new File(Objects.requireNonNull(url).getFile());
        for(File file : Objects.requireNonNull(classDir.listFiles())){

            //递归获取scan下所有的类，并将类名添加至classNames中用于之后实例化
            if(file.isDirectory()){
                doScanner(packageName + "." + file.getName());
            }else {
                String className = packageName + "." + file.getName().replace(".class","").trim();
                classNames.add(className);
            }
        }

    }

    //进行实例化
    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }
        for(String className : classNames){
            try {
                Class<?> clazz = Class.forName(className);

                //进行实例化
                //判断不是所有的都需要实例化，只有添加了例如Controller或者Service的注解才初始化
                if(clazz.isAnnotationPresent(SWController.class)){
                    //beanName beanId
                    //1.默认采用类名的首字母小写
                    //2.如果自定义了名字，默认使用自定义名字
                    //3.根据类型匹配，利用实现类的接口名作为Key
                    String beanName = toLowStr(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                }else if(clazz.isAnnotationPresent(SWService.class)){
                    SWService swService = clazz.getAnnotation(SWService.class);
                    String beanName = swService.value();
                    if(!"".equals(beanName.trim())){
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }

                    //获取对象所实现的所有接口
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i : interfaces){
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                }
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }


        }
    }

    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()){
            //在spring中没有隐私
            //咱们只认 @Autowried，获取所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for(Field field : fields){
                if(!field.isAnnotationPresent(SWAutowried.class)){
                    continue;
                }

                SWAutowried swAutowried = field.getAnnotation(SWAutowried.class);

                String beanName = swAutowried.value().trim();
                //如果为空说明使用默认名字，所以使用getName获取默认名字
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                field.setAccessible(true);

                try {
                    //向属性set进已经实例化的对象
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void initHandlerMapping() {
        if(!ioc.isEmpty()){
            for(Map.Entry<String, Object> entry : ioc.entrySet()){
                Class<?> clazz = entry.getValue().getClass();
                //HandlerMapping只认识SWController
                if(!clazz.isAnnotationPresent(SWController.class)){
                    continue;
                }
                String url = "";
                //获取类上的RequestMapping地址
                if(clazz.isAnnotationPresent(SWRequestMapping.class)){
                    SWRequestMapping swRequestMapping = clazz.getAnnotation(SWRequestMapping.class);
                    url = swRequestMapping.value();
                }
                Method[] methods = clazz.getMethods();
                for(Method method : methods){
                    if(!method.isAnnotationPresent(SWRequestMapping.class)){
                        continue;
                    }
                    //获取实际方法上的requestMapping
                    SWRequestMapping swRequestMapping = method.getAnnotation(SWRequestMapping.class);
                    String mUrl = url + swRequestMapping.value();
                    handlerMapping.put(mUrl, method);
                    System.out.println("Mapping : " + mUrl + " " + method);
                }

            }
        }
    }

    private void doDispatch(HttpServletRequest request, HttpServletResponse response) {
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if(!handlerMapping.containsKey(url)){
            try {
                response.getWriter().write("404");
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //获取当前路由对应的方法
        Method method = handlerMapping.get(url);
        System.out.println("获得对应的方法" + method);

        //获取方法列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        Map<String, String[]> parameterMap = request.getParameterMap();

        Object[] paramValues = new Object[parameterTypes.length];
        for(int i = 0; i < parameterTypes.length; i++){
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = request;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = response;
            }else if(parameterType == String.class){
                for(Map.Entry<String, String[]> entry : parameterMap.entrySet()){
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]","")
                            .replaceAll(",\\s", ",");
                    paramValues[i++] = value;
                    if(i == parameterTypes.length){
                        break;
                    }
                }
            }
        }
        try{
            String beanName = toLowStr(method.getDeclaringClass().getSimpleName());
            method.invoke(this.ioc.get(beanName), paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    private String toLowStr(String str){
        char[] ch = str.toCharArray();
        ch[0] += 32;
        return String.valueOf(ch);
    }
}

