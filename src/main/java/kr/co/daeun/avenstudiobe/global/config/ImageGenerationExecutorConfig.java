package kr.co.daeun.avenstudiobe.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ImageGenerationExecutorConfig {

    @Bean("imageGenerationJobExecutor")
    public TaskExecutor imageGenerationJobExecutor(
            @Value("${image-generation.job-executor.core-size:1}") int coreSize,
            @Value("${image-generation.job-executor.max-size:2}") int maxSize,
            @Value("${image-generation.job-executor.queue-capacity:20}") int queueCapacity
    ) {
        return executor("image-job-", coreSize, maxSize, queueCapacity);
    }

    @Bean("imageGenerationTaskExecutor")
    public TaskExecutor imageGenerationTaskExecutor(
            @Value("${image-generation.task-executor.pool-size:4}") int poolSize,
            @Value("${image-generation.task-executor.queue-capacity:100}") int queueCapacity
    ) {
        return executor("image-task-", poolSize, poolSize, queueCapacity);
    }

    private TaskExecutor executor(String prefix, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
