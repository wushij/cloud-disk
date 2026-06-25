package com.clouddisk.controller;



import com.alibaba.csp.sentinel.annotation.SentinelResource;

import com.alibaba.csp.sentinel.slots.block.BlockException;

import com.clouddisk.common.BusinessException;

import com.clouddisk.dto.Md5CheckRequest;

import com.clouddisk.dto.UploadInitRequest;

import com.clouddisk.entity.FileRecord;

import com.clouddisk.service.UploadService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;



import java.util.Map;



@RestController

@RequestMapping("/api/upload")

@RequiredArgsConstructor

public class UploadController {



    private final UploadService uploadService;



    @PostMapping("/check-md5")

    @SentinelResource(value = "upload_api", blockHandler = "uploadBlocked")

    public Map<String, Object> checkMd5(@RequestBody Md5CheckRequest req) {

        return uploadService.checkMd5(req);

    }



    @PostMapping("/init")

    @SentinelResource(value = "upload_api", blockHandler = "uploadBlocked")

    public Map<String, Object> init(@RequestBody UploadInitRequest req) {

        return uploadService.init(req);

    }



    @PostMapping("/chunk")

    @SentinelResource(value = "upload_api", blockHandler = "uploadBlocked")

    public Map<String, String> chunk(

            @RequestParam String uploadId,

            @RequestParam int chunkIndex,

            @RequestParam("file") MultipartFile file) throws Exception {

        uploadService.uploadChunk(uploadId, chunkIndex, file);

        return Map.of("message", "ok");

    }



    @PostMapping("/merge")

    @SentinelResource(value = "upload_api", blockHandler = "uploadBlocked")

    public FileRecord merge(@RequestBody Map<String, String> body) throws Exception {

        return uploadService.merge(body.get("uploadId"), body.get("mimeType"));

    }



    @GetMapping("/{uploadId}/resume")

    @SentinelResource(value = "upload_api", blockHandler = "uploadBlocked")

    public Map<String, Object> resume(@PathVariable String uploadId) {

        return uploadService.resume(uploadId);

    }



    public static Map<String, Object> uploadBlocked(BlockException ex) {

        throw new BusinessException("上传过于频繁，请稍后再试");

    }

}

