package com.pmsjl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/***
 * 作用：限制这个注解只能用在方法上。
 */
@Target(ElementType.METHOD)
/***
 * 作用：指定注解在运行时仍然保留
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     *
     * @return
     */
    String mustRole() default "";

}

