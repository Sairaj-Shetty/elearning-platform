package com.lumina.web;

import com.lumina.api.Requests.TicketRequest;
import com.lumina.api.Views.LessonView;
import com.lumina.api.Views.TicketView;
import com.lumina.common.ApiResponse;
import com.lumina.media.MediaService;
import com.lumina.security.AuthUser;
import com.lumina.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(value = "/instructor/courses/{courseId}/lessons/{lessonId}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LessonView> uploadVideo(@PathVariable String courseId, @PathVariable String lessonId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(media.uploadVideo(SecurityUtils.requireUser(), courseId, lessonId, file));
    }

    @DeleteMapping("/instructor/courses/{courseId}/lessons/{lessonId}/video")
    public ApiResponse<LessonView> deleteVideo(@PathVariable String courseId, @PathVariable String lessonId) {
        return ApiResponse.ok(media.deleteVideo(SecurityUtils.requireUser(), courseId, lessonId));
    }

    @PostMapping(value = "/instructor/courses/{courseId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> thumbnail(@PathVariable String courseId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(media.uploadThumbnail(SecurityUtils.requireUser(), courseId, file));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> avatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(media.uploadAvatar(SecurityUtils.requireUser(), file));
    }

    @PostMapping("/media/tickets")
    public ApiResponse<TicketView> ticket(@Valid @RequestBody TicketRequest request) {
        return ApiResponse.ok(media.ticket(SecurityUtils.requireUser(), request.courseId(), request.lessonId()));
    }

    @GetMapping("/media/videos/{courseId}/{lessonId}")
    public ResponseEntity<StreamingResponseBody> stream(
            @PathVariable String courseId,
            @PathVariable String lessonId,
            @RequestParam(required = false) String ticket,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) throws Exception {
        AuthUser actor = SecurityUtils.currentUserOrNull();
        Resource resource = media.stream(courseId, lessonId, ticket, actor);
        long length = resource.contentLength();
        String type = media.contentType(courseId, lessonId);
        List<HttpRange> ranges = rangeHeader == null ? List.of() : HttpRange.parseRanges(rangeHeader);
        if (ranges.isEmpty()) {
            StreamingResponseBody body = output -> {
                try (InputStream in = resource.getInputStream()) {
                    in.transferTo(output);
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(type))
                    .contentLength(length)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(body);
        }
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        long count = end - start + 1;
        StreamingResponseBody body = output -> {
            try (InputStream in = resource.getInputStream()) {
                in.skipNBytes(start);
                byte[] buffer = new byte[8192];
                long remaining = count;
                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) break;
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        };
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length)
                .contentLength(count)
                .body(body);
    }

    @GetMapping("/media/files/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        String key = uri.substring(uri.indexOf("/files/") + "/files/".length());
        Resource resource = media.publicImage(key);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (key.endsWith(".png")) type = MediaType.IMAGE_PNG;
        else if (key.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        else if (key.endsWith(".jpg") || key.endsWith(".jpeg")) type = MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(resource);
    }
}
