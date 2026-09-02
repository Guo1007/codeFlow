package gcy.codeflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 扫描两个包：gcy.codeflow（项目自有代码）+ com.aidev.agent（Agent 引擎代码）
 */
@SpringBootApplication(scanBasePackages = {"gcy.codeflow", "com.aidev.agent"})
public class CodeFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeFlowApplication.class, args);
    }

}
