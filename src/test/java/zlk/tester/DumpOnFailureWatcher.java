package zlk.tester;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit5 用: テストが失敗したときに、最後に登録された classBytes を javap でダンプする拡張。
 *
 * 使い方:
 *
 *   @ExtendWith(DumpOnFailureWatcher.class)
 *   class BytecodeTest {
 *       @Test
 *       void testSomething() {
 *           byte[] classBytes = generateBytesSomehow();
 *           DumpOnFailureWatcher.setLastClassDump("MyGeneratedClass", classBytes);
 *           ...
 *       }
 *   }
 */
public class DumpOnFailureWatcher implements TestWatcher, BeforeEachCallback {

    /** テストごとに保持する最新のダンプ対象（クラス名ヒント＋バイトコード）。 */
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DumpOnFailureWatcher.class);

    private record LastDump(String classNameHint, byte[] classBytes) {}

    /**
     * テストコードから呼び出すヘルパー。
     * 「このクラスバイト列を、失敗時に javap ダンプしてほしい」という情報を登録する。
     */
    public static void setLastClassDump(String classNameHint, byte[] classBytes) {
        // ExtensionContext はここでは直接触れられないので、
        // スレッドローカルな static フィールドに一旦保存して、
        // BeforeEachCallback で Store に移す、という案もあるのですが、
        // とりあえずシンプルに「static フィールドに保持」で済ませます。
        // （並列実行をしない前提 / まずは動くもの）
        lastDump = new LastDump(classNameHint, classBytes.clone());
    }

    // ★簡単のため、並列実行非対応の static フィールド版。
    //   必要になったら ExtensionContext の Store に載せ替えるとよいです。
    private static volatile LastDump lastDump;

    @Override
    public void beforeEach(ExtensionContext context) {
        // テストごとに前回の情報をクリア
        lastDump = null;
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        LastDump dump = lastDump;
        if (dump == null || dump.classBytes == null) {
            // まだ classBytes が生成される前に落ちているなど。
            System.err.println("[DumpOnFailureWatcher] test failed, but no classBytes registered.");
            return;
        }

        String displayName = context.getDisplayName();
        System.err.println("[DumpOnFailureWatcher] Test failed: " + displayName);
        cause.printStackTrace(System.err);

        String javapOutput = runJavap(dump.classBytes, dump.classNameHint());

        System.err.println("======= javap -c -l dump (" + dump.classNameHint() + ") =======");
        System.err.println(javapOutput);
        System.err.println("================================================================");
    }

    /**
     * クラスバイト列を一時ファイルに書き出し、javap -c -l を実行して結果を返す。
     */
    private static String runJavap(byte[] classBytes, String classNameHint) {
        try {
            Path tempDir = Files.createTempDirectory("javap_dump_");
            String simpleName = classNameHint.replace('.', '_');
            Path classFile = tempDir.resolve(simpleName + ".class");

            Files.write(classFile, classBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "javap", "-c", "-l", "-v",
                    classFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader br =
                         new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String out = br.lines().collect(Collectors.joining(System.lineSeparator()));
                int exitCode = proc.waitFor();
                if (exitCode != 0) {
                    return "(javap exited with code " + exitCode + ")\n" + out;
                }
                return out;
            } finally {
                // tempDir は念のため残しておきたいなら削除しなくてもよい。
                // 気になる場合は Files.deleteIfExists(classFile) などで掃除する。
            }
        } catch (Exception e) {
            return "(javap failed: " + e + ")";
        }
    }

    // TestWatcher の他メソッドは必要に応じて
    @Override
    public void testSuccessful(ExtensionContext context) {
        // 成功時は何もしない
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        // 何もしない
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        // 何もしない
    }
}
