package lambdasinaction.appd;

import java.util.function.Function;

/**
 * 每个匿名类都会产生一个新的.class文件
 * 每个匿名类都会为类或者接口产生一个新的子类型
 */
public class InnerClass {
    Function<Object, String> f = new Function<Object, String>() {
        @Override
        public String apply(Object obj) {
            return obj.toString();
        }
    };
}
