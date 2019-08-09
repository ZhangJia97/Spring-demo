package xyz.suiwo.framework.annotation;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SWAutowried {
    String value() default "";
}
