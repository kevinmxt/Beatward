package dev.beatward.notification.api;

import dev.beatward.auth.CurrentUserProvider;
import dev.beatward.common.PageResponse;
import dev.beatward.notification.NotificationChannelEntity;
import dev.beatward.notification.NotificationChannelService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-channels")
public class NotificationChannelController {

    private final NotificationChannelService channelService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationChannelController(NotificationChannelService channelService,
                                         CurrentUserProvider currentUserProvider) {
        this.channelService = channelService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageResponse<NotificationChannelResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        Page<NotificationChannelEntity> result = channelService.list(userId, page, size);
        List<NotificationChannelResponse> items = result.getContent().stream()
                .map(channelService::toResponse)
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements());
    }

    @PostMapping
    public ResponseEntity<NotificationChannelResponse> create(
            @RequestBody(required = false) NotificationChannelCreateRequest request,
            Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        NotificationChannelEntity channel = channelService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(channelService.toResponse(channel));
    }

    @PutMapping("/{id}")
    public NotificationChannelResponse update(
            @PathVariable Long id,
            @RequestBody(required = false) NotificationChannelUpdateRequest request,
            Authentication authentication) {
        Long userId = currentUserProvider.requireId(authentication);
        return channelService.toResponse(channelService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        channelService.delete(currentUserProvider.requireId(authentication), id);
    }

    @PostMapping("/{id}/test")
    public NotificationChannelTestResponse test(@PathVariable Long id,
                                                Authentication authentication) {
        return channelService.test(currentUserProvider.requireId(authentication), id);
    }
}
