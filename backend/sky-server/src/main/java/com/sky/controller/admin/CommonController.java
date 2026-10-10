package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.service.LocalFileStorage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
@RequiredArgsConstructor
public class CommonController {

    private final LocalFileStorage localFileStorage;

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file){
        try {
            return Result.success(localFileStorage.store(file));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            log.warn("图片上传失败：{}", e.getMessage());
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
