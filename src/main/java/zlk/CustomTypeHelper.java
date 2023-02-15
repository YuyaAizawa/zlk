package zlk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class CustomTypeHelper {
	private final String supertype;
	private final List<String> subtypes;
	private final List<String> forNames;
	private final List<String> locals;
	private final int size;

	public CustomTypeHelper(String supertype, List<String> subtypes, List<String> forNames, List<String> locals) {
		if(subtypes.size() != locals.size() || locals.size() != forNames.size()) {
			throw new IllegalArgumentException();
		}
		this.supertype = supertype;
		this.subtypes = subtypes;
		this.forNames = forNames;
		this.locals = locals;
		this.size = subtypes.size();
	}

	public void printFold(PrintWriter pw) {
		pw.printf("default <R> R fold(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tFunction<? super %s, ? extends R> %s,%n",
					subtypes.get(i), forNames.get(i));
		}
		pw.printf("\t\tFunction<? super %s, ? extends R> %s) {%n",
				subtypes.get(size-1), forNames.get(size-1));
		pw.print("\t");

		for(int i = 0;i < size;i++) {
			pw.printf("if(this instanceof %s %s) {%n",
					subtypes.get(i), locals.get(i));
			pw.printf("\t\treturn %s.apply(%s);%n",
					forNames.get(i), locals.get(i));
			pw.printf("\t} else ");
		}
		pw.printf("{ %n");
		pw.printf("\t\tthrow new Error(this.getClass().toString());%n");
		pw.printf("\t}%n");
		pw.printf("}%n");
	}

	public void printMatch(PrintWriter pw) {
		pw.printf("default void match(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tConsumer<? super %s> %s,%n",
					subtypes.get(i), forNames.get(i));
		}

		pw.printf("\t\tConsumer<? super %s> %s) {%n",
				subtypes.get(size-1), forNames.get(size-1));
		pw.print("\t");

		for(int i = 0;i < size;i++) {
			pw.printf("if(this instanceof %s %s) {%n",
					subtypes.get(i), locals.get(i));
			pw.printf("\t\t%s.accept(%s);%n",
					forNames.get(i), locals.get(i));
			pw.printf("\t} else ");
		}
		pw.printf("{ %n");
		pw.printf("\t\tthrow new Error(this.getClass().toString());%n");
		pw.printf("\t}%n");
		pw.printf("}%n");
	}

	private void printAll(PrintWriter pw) {
		printFold(pw);
		pw.println();
		printMatch(pw);
		pw.flush();
	}

	public void printFoldNoInstanceofSupertype(PrintWriter pw) {
		pw.printf("public <R> R fold(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tFunction<? super %s, ? extends R> %s,%n",
					subtypes.get(i), forNames.get(i));
		}
		pw.printf("\t\tFunction<? super %s, ? extends R> %s);%n",
				subtypes.get(size-1), forNames.get(size-1));
	}

	public void printMatchNoInstanceofSupertype(PrintWriter pw) {
		pw.printf("public void match(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tConsumer<? super %s> %s,%n",
					subtypes.get(i), forNames.get(i));
		}
		pw.printf("\t\tConsumer<? super %s> %s);%n",
				subtypes.get(size-1), forNames.get(size-1));
	}

	public void printFoldNoInstanceofSubtype(int idx, PrintWriter pw) {
		pw.printf("@Override%n");
		pw.printf("public <R> R fold(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tFunction<? super %s, ? extends R> %s,%n",
					subtypes.get(i), forNames.get(i));
		}
		pw.printf("\t\tFunction<? super %s, ? extends R> %s) {%n",
				subtypes.get(size-1), forNames.get(size-1));

		pw.printf("\treturn %s.apply(this);%n",
				forNames.get(idx));
		pw.printf("}%n");
	}

	public void printMatchNoInstanceofSubtype(int idx, PrintWriter pw) {
		pw.printf("@Override%n");
		pw.printf("public void match(%n");
		for(int i = 0;i < size-1;i++) {
			pw.printf("\t\tConsumer<? super %s> %s,%n",
					subtypes.get(i), forNames.get(i));
		}
		pw.printf("\t\tConsumer<? super %s> %s) {%n",
				subtypes.get(size-1), forNames.get(size-1));

		pw.printf("\t%s.accept(this);%n",
				forNames.get(idx));
		pw.printf("}%n");
	}

	public void printNoInstanceofSubtype(int idx, PrintWriter pw) {
		pw.printf("public class %s implements %s {%n%n%n",
				subtypes.get(idx), supertype);

		printFoldNoInstanceofSubtype(idx, pw);
		pw.println();

		printMatchNoInstanceofSubtype(idx, pw);
		pw.println();

		pw.printf("}%n");
	}

	public void printAllNoInstanceof(PrintWriter pw) {

		pw.printf("public sealed interface %s%npermits ",
				supertype);
		for(int i = 0; i < size; i++) {
			if(i != 0) {
				pw.print(", ");
			}
			pw.print(subtypes.get(i));
		}
		pw.println(" {");
		pw.println();pw.println();

		printFoldNoInstanceofSupertype(pw);
		pw.println();
		printMatchNoInstanceofSupertype(pw);
		pw.println();
		pw.println("}");
		pw.println();

		for(int i = 0; i < size; i++) {
			printNoInstanceofSubtype(i, pw);
			pw.println();
		}

		pw.flush();
	}



	public static void main(String[] args) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter pw = new PrintWriter(System.out);

//		String supertypr = "Constraint";
//		List<String> subtypes = Arrays.asList("NoExpection", "FromContext", "FromAnnotation");
//		List<String> forNames = Arrays.asList("forNoExp", "forContext", "forAnnotarion");
//		List<String> locals = Arrays.asList("noExp", "context", "annotation");
//
//		new CustomTypeHelper(subtypes, forNames, locals).printAll(pw);

		readAll(br).printAll(pw);
	}

	public static CustomTypeHelper readAll(BufferedReader br) throws IOException {
		System.out.print("supertype: ");
		String supertype = br.readLine();
		System.out.print("subtypes: ");
		List<String> subtypes = readList(br);
		System.out.print("forNames: ");
		List<String> forNames = readList(br);
		System.out.print("locals: ");
		List<String> locals = readList(br);

		return new CustomTypeHelper(supertype, subtypes, forNames, locals);
	}

	public static final Pattern pattern = Pattern.compile("(\\w+)");
	public static List<String> readList(BufferedReader br) throws IOException {
		return pattern.matcher(br.readLine()).results().map(MatchResult::group).toList();
	}





}
