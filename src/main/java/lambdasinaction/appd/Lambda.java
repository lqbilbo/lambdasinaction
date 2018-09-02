package lambdasinaction.appd;

import java.util.function.Function;

/**
 * 利用invokedynamic代替创建额外类，将字节码的转换推迟到运行时。
 * 仅在首次被调用时需要转换
 */
public class Lambda {
    Function<Object, String> f = Object::toString;
}
