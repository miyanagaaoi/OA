import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 开发夹具口令哈希生成器（源码入库，**不含任何口令**）。
 *
 * 用项目已有依赖 spring-security-crypto 生成本机开发账号的 BCrypt 哈希，
 * 与 com.oa.common.security.PasswordService 同源，避免手写/网搜哈希造成口径不一致。
 *
 * 编译与运行（Windows PowerShell，仓库根目录执行；本仓库无聚合 pom，需 -f 指定模块）：
 *   mvn -q -f oa-server/pom.xml -DskipTests dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\oa-fixture-cp.txt"
 *   $cp = Get-Content "$env:TEMP\oa-fixture-cp.txt" -Raw      # -cp 收的是 classpath 字符串，不是文件路径
 *   javac -cp $cp -d "$env:TEMP\oa-fixture-tools" oa-deploy/fixtures/tools/GenHash.java
 *   java -cp "$env:TEMP\oa-fixture-tools;$cp" GenHash "<你的口令>" 12
 *
 * 产物：只向 stdout 打印哈希与自校验结果，不写文件、不回显口令。
 * 详见 ../90-dev-admin.md。
 */
public class GenHash {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("用法: java GenHash <口令> [strength=12]");
            System.exit(2);
        }
        String raw = args[0];
        int strength = args.length > 1 ? Integer.parseInt(args[1]) : 12;

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        String hash = encoder.encode(raw);

        // 不打印明文口令本身。
        System.out.println("strength = " + strength);
        System.out.println("hash     = " + hash);
        System.out.println("matches  = " + encoder.matches(raw, hash));
        System.out.println("wrongPwd = " + encoder.matches("wrong-password", hash));
    }
}
