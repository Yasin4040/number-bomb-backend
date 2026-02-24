package com.numberbomb.controller;

import com.numberbomb.service.MatchService;
import com.numberbomb.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {
    
    private final MatchService matchService;
    
    @PostMapping("/start")
    public Result<?> startMatch(@RequestBody MatchSettingDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.startMatch(userId, dto.getMode(), dto.getRankLevel()));
    }
    
    @PostMapping("/cancel")
    public Result<?> cancelMatch(@RequestBody CancelMatchDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        matchService.cancelMatch(userId, dto.getMatchId());
        return Result.success();
    }
    
    @GetMapping("/status")
    public Result<?> getMatchStatus(@RequestParam String matchId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.getMatchStatus(userId, matchId));
    }
    
    @Data
    public static class MatchSettingDTO {
        private String mode;
        private Integer rankLevel;
    }
    
    @Data
    public static class CancelMatchDTO {
        private String matchId;
    }
}
