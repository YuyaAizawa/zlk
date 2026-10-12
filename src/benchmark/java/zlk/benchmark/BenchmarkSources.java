package zlk.benchmark;

import java.util.StringJoiner;

/** 組込み定義のoptionsに依存しない題材生成．case集合は定義annotationが所有する． */
public final class BenchmarkSources {
	private BenchmarkSources() {}
	public enum Subject {
		RECORD_SINGLE_FIELD, RECORD_MULTI_FIELD, RECORD_SHARED, OPERAND_STACK, NAMED_LOCAL, COMBINED;
		public String benchmarkName() {
			return switch(this) {
			case RECORD_SINGLE_FIELD -> "RecordSingleFieldBenchmark";
			case RECORD_MULTI_FIELD -> "RecordMultiFieldBenchmark";
			case RECORD_SHARED -> "RecordSharedBenchmark";
			case OPERAND_STACK -> "OperandStackBenchmark";
			case NAMED_LOCAL -> "NamedLocalBenchmark";
			case COMBINED -> "CombinedBenchmark";
			};
		}
	}
	public record Sample(Subject subject, int fields, int updates, int depth) {
		public Sample {
			if(subject == null || fields < 0 || updates < 0 || depth < 0)
				throw new IllegalArgumentException("invalid sample");
			boolean record = subject != Subject.OPERAND_STACK && subject != Subject.NAMED_LOCAL;
			if(record ? fields < 1 || updates < 1 || depth != 0 : fields != 0 || updates != 0)
				throw new IllegalArgumentException("incompatible parameters");
			if((subject == Subject.RECORD_MULTI_FIELD || subject == Subject.COMBINED) && fields < 4)
				throw new IllegalArgumentException("four updated fields required");
			if(subject == Subject.RECORD_SHARED && updates != 1 || subject == Subject.OPERAND_STACK && depth < 1
					|| subject == Subject.NAMED_LOCAL && depth != 0)
				throw new IllegalArgumentException("incompatible parameters");
		}
		public int updatedFields() {
			return switch(subject) {
			case RECORD_MULTI_FIELD, COMBINED -> 4;
			case RECORD_SINGLE_FIELD, RECORD_SHARED -> 1;
			case OPERAND_STACK, NAMED_LOCAL -> 0;
			};
		}
		public String source() {
			if(subject == Subject.OPERAND_STACK) {
				String exp = "x";
				for(int i = 0; i < depth; i++) exp = "add (" + exp + ") (mul x x)";
				return "module Bench\nbench x = " + exp + "\n";
			}
			if(subject == Subject.NAMED_LOCAL)
				return "module Bench\nbench x =\n  let\n    sum = add x x\n    product = mul sum sum\n  in\n    product\n";
			StringBuilder src = new StringBuilder("module Bench\nbench x =\n  let\n    r0 = ");
			StringJoiner initial = new StringJoiner(", ", "{ ", " }\n");
			for(int f = 0; f < fields; f++) initial.add("f" + f + " = add x " + (f + 1));
			src.append(initial);
			for(int u = 0; u < updates; u++) {
				if(subject != Subject.COMBINED) src.append("    v").append(u).append(" = add x ").append(u + 1).append('\n');
				StringJoiner changed = new StringJoiner(", ", "{ r" + u + " | ", " }\n");
				for(int j = 0; j < updatedFields(); j++) {
					int f = (u * updatedFields() + j) % fields;
					String value = subject == Subject.COMBINED ? "add (mul x x) (add x " + (u + f + 1) + ")" : "v" + u;
					changed.add("f" + f + " = " + value);
				}
				src.append("    r").append(u + 1).append(" = ").append(changed);
			}
			src.append("  in\n    ");
			src.append(subject == Subject.RECORD_SHARED ? "{ original = r0, changed = r1 }" : "r" + updates);
			return src.append('\n').toString();
		}
	}
}
