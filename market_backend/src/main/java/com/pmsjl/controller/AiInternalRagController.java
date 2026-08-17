package com.pmsjl.controller;

import com.pmsjl.model.dto.ai.internal.PostRagSnapshotResponse;
import com.pmsjl.service.AiRagSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("internal/ai/rag")
public class AiInternalRagController {

    @Autowired
    private AiRagSnapshotService aiRagSnapshotService;

    @GetMapping("/posts")
    public PostRagSnapshotResponse listPostSnapshots(
            @RequestParam(defaultValue = "0") Long afterId,
            @RequestParam(defaultValue = "200") Integer limit,
            HttpServletRequest request
    ) {
        return aiRagSnapshotService.listPostSnapshots(
                afterId,
                limit,
                request
        );
    }
}
