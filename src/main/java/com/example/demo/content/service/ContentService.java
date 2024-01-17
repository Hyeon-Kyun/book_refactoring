package com.example.demo.content.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.demo.category.model.Category;
import com.example.demo.content.model.Content;
import com.example.demo.content.model.ContentImage;
import com.example.demo.content.model.dto.*;
import com.example.demo.content.repository.ContentImageRepository;
import com.example.demo.content.repository.ContentRepository;
import com.example.demo.writer.model.Writer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentImageRepository contentImageRepository;
    private final AmazonS3 s3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 작품 추가
    @Transactional
    public ContentCreateRes create(ContentCreateReq contentCreateReq,
                                   MultipartFile uploadFiles) {

        Content content = contentRepository.save(Content.builder()
                .categoryId(contentCreateReq.getCategory_id())
                .writerId(contentCreateReq.getWriter_id())
                .name(contentCreateReq.getName())
                .classify(contentCreateReq.getClassify())
                .build());

            String uploadPath = uplopadFile(uploadFiles);

            ContentImage contentImage = contentImageRepository.save(ContentImage.builder()
                    .content(content)
                    .filename(uploadPath)
                    .build());


        ContentCreateRes response = ContentCreateRes.builder()
                .classify(content.getClassify())
                .name(content.getName())
                .filename(contentImage.getFilename())
                .build();

        return response;
    }

    // 작품 전체 리스트 보기 완료
    public List<ContentReadRes> list(Integer page, Integer size){
        Pageable pageable = PageRequest.of(page-1,size);
        Page<Content> result = contentRepository.findList(pageable);

        List<ContentReadRes> contentReadResList = new ArrayList<>();

        for (Content content : result.getContent()) {

            ContentImage contentImages = content.getContentImages();
            String filename = contentImages.getFilename();

            ContentReadRes contentReadRes = ContentReadRes.builder()
                    .id(content.getId())
                    .classify(content.getClassify())
                    .categoryId(content.getCategoryId().getId())
                    .writerId(content.getWriterId().getId())
                    .name(content.getName())
                    .filename(filename)
                    .build();

            contentReadResList.add(contentReadRes);
        }


        return contentReadResList;
    }

    // 작품 id 검색 완료
    public ContentReadRes read(Long id) {
        Optional<Content> result = contentRepository.findById(id);

        if (result.isPresent()) {
            Content content = result.get();

            ContentImage contentImage = content.getContentImages();

                String filename = contentImage.getFilename();

            ContentReadRes contentReadRes = ContentReadRes.builder()
                    .id(content.getId())
                    .classify(content.getClassify())
                    .name(content.getName())
                    .categoryId(content.getCategoryId().getId())
                    .writerId(content.getWriterId().getId())
                    .filename(filename)
                    .build();

            return contentReadRes;
        }
        return null;
    }

    public void update(ContentUpdateReq contentUpdateReq, MultipartFile uploadFiles) {
        Optional<Content> result = contentRepository.findById(contentUpdateReq.getId());
        Optional<ContentImage> imageResult = contentImageRepository.findByContent_Id(result.get().getId());

        if (result.isPresent()) {
            ContentImage image = imageResult.get();
            Content content = result.get();
            if (contentUpdateReq.getName() != null)
                content.setName(contentUpdateReq.getName());

            if (contentUpdateReq.getClassify() != null)
                content.setClassify(contentUpdateReq.getClassify());
            if (contentUpdateReq.getCategoryId() != null)
                content.getCategoryId().setId(contentUpdateReq.getCategoryId());

            if (uploadFiles != null)
                image.setFilename(uplopadFile(uploadFiles));

            contentRepository.save(content);
            contentImageRepository.save(image);
        }
    }


    // 작품 삭제
    @Transactional
    public void delete(Long id) {
        Optional<Content> result = contentRepository.findById(id);

        if (result.isPresent()) {
            Content content = result.get();
            content.setCategoryId(null);
            content.setWriterId(null);

            contentRepository.save(content);


            Optional<ContentImage> image  = contentImageRepository.findByContent_Id(id);
            if(image.isPresent()){
                ContentImage contentImage = image.get();
                contentImage.setContent(null);

                contentImageRepository.save(contentImage);
                contentImageRepository.deleteById(contentImage.getId());
            }

            contentRepository.deleteById(id);
        }

    }




    // 작품 이미지 파일 업로드
    public String makeFolder() {
        String str = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String folderPath = str.replace("/", File.separator);

        return folderPath;
    }


    public String uplopadFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String folderPath = makeFolder();
        String uuid = UUID.randomUUID().toString();
        String saveFileName = folderPath + File.separator + uuid + "_" + originalName;

        InputStream input = null;
        try {
            input = file.getInputStream();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());


            s3.putObject(bucket, saveFileName.replace(File.separator, "/"), input, metadata);

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return s3.getUrl(bucket, saveFileName.replace(File.separator, "/")).toString();
    }



}
