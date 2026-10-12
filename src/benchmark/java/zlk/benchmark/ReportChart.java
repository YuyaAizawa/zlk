package zlk.benchmark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

/** 静的reportの図定義．xとseries以外のパラメータは図の分割条件となる． */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ReportChart.Charts.class)
public @interface ReportChart {
	String x();
	String[] series() default {};
	Metric[] metrics() default {Metric.CODE_LENGTH};
	Plot plot() default Plot.LINE;
	/** Mermaid 11.17以降の名前付き系列構文．古いGitHub renderer向けにはfalse． */
	boolean namedSeries() default false;

	enum Plot { LINE, BAR }
	enum Metric {
		TOTAL_CLASS_BYTES, MODULE_CLASS_BYTES, CLASS_COUNT, CODE_LENGTH,
		ALOAD, ASTORE, MAX_LOCALS, MAX_STACK, UPDATE, INPLACE_UPDATE;
		public String column() { return name().toLowerCase(Locale.ROOT); }
	}
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@interface Charts { ReportChart[] value(); }
}
