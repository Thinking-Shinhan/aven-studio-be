package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationResult;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationJobRepository;
import kr.co.daeun.avenstudiobe.domain.imagegeneration.repository.ImageGenerationResultRepository;
import kr.co.daeun.avenstudiobe.global.exception.ImageGenerationJobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageGenerationProgressService {

    private final ImageGenerationJobRepository jobRepository;
    private final ImageGenerationResultRepository resultRepository;

    @Transactional
    public void markProcessing(Long jobId) {
        ImageGenerationJob job = findLockedJob(jobId);
        job.markProcessing();
    }

    @Transactional
    public void recordGeneratedResult(Long jobId, Asset asset, int outputIndex) {
        ImageGenerationJob job = findLockedJob(jobId);
        resultRepository.save(ImageGenerationResult.generated(job, asset, outputIndex));
        job.incrementOutputCount();
    }

    @Transactional
    public void markSucceeded(Long jobId, int latencyMs) {
        ImageGenerationJob job = findLockedJob(jobId);
        job.markSucceeded(job.getOutputCount(), latencyMs);
    }

    @Transactional
    public void markFailed(Long jobId, String errorMessage, int latencyMs) {
        ImageGenerationJob job = findLockedJob(jobId);
        job.markFailed(errorMessage, latencyMs);
    }

    private ImageGenerationJob findLockedJob(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(ImageGenerationJobNotFoundException::new);
    }
}
