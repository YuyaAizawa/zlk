package zlk.benchmark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 実測の比較棒グラフ．series指定時はintのxとbooleanのseries，省略時はbooleanのxを比較する．残りの引数で図を分割する． */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JmhChart {
	String x();
	String series() default "";
}
