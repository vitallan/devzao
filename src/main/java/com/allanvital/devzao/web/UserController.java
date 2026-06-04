package com.allanvital.devzao.web;

import com.allanvital.devzao.Constants;
import com.allanvital.devzao.async.LiveUserRequestService;
import com.allanvital.devzao.config.GitConfig;
import com.allanvital.devzao.domain.*;
import com.allanvital.devzao.web.RepoCommitChartView.RepoCommitInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.UriUtils;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
* @author Allan Vital (https://allanvital.com)
  */
@Controller
public class UserController {

    private static final DateTimeFormatter FETCHED_AT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FETCHED_AT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final GitHubUserRepository gitHubUserRepository;
    private final GitHubUserPhotoRepository gitHubUserPhotoRepository;
    private final GitHubRepositoryRepository gitHubRepositoryRepository;
    private final LiveUserRequestService liveUserRequestService;
    private final GitCommitAnalysisRepository gitCommitAnalysisRepository;
    private final GitMonthlyActivityRepository gitMonthlyActivityRepository;
    private final GitHourlyActivityRepository gitHourlyActivityRepository;
    private final GitWeekdayActivityRepository gitWeekdayActivityRepository;
    private final GitConfig gitConfig;

    public UserController(
            GitHubUserRepository gitHubUserRepository,
            GitHubUserPhotoRepository gitHubUserPhotoRepository,
            GitHubRepositoryRepository gitHubRepositoryRepository,
            LiveUserRequestService liveUserRequestService,
            GitCommitAnalysisRepository gitCommitAnalysisRepository,
            GitMonthlyActivityRepository gitMonthlyActivityRepository,
            GitHourlyActivityRepository gitHourlyActivityRepository,
            GitWeekdayActivityRepository gitWeekdayActivityRepository,
            GitConfig gitConfig
    ) {
        this.gitHubUserRepository = gitHubUserRepository;
        this.gitHubUserPhotoRepository = gitHubUserPhotoRepository;
        this.gitHubRepositoryRepository = gitHubRepositoryRepository;
        this.liveUserRequestService = liveUserRequestService;
        this.gitCommitAnalysisRepository = gitCommitAnalysisRepository;
        this.gitMonthlyActivityRepository = gitMonthlyActivityRepository;
        this.gitHourlyActivityRepository = gitHourlyActivityRepository;
        this.gitWeekdayActivityRepository = gitWeekdayActivityRepository;
        this.gitConfig = gitConfig;
    }

    @GetMapping("/random")
    public String randomProfile() {
        Optional<GitHubUser> user = this.gitHubUserRepository.findRandomFetchedUser();
        if (user.isEmpty()) {
            log.info("GET /random — no fetched users available");
            return "redirect:/";
        }
        String login = user.get().getLogin();
        log.info("GET /random — redirecting to {}", login);
        return "redirect:/u/" + login;
    }

    @GetMapping("/u/{login}")
    public String user(@PathVariable("login") String login, Model model) {
        String trimmed = login == null ? "" : login.trim();
        if (!trimmed.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$")) {
            return redirectHome(HomeMessage.GITHUB_USER_NOT_FOUND, trimmed);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);

        GitHubUser user = this.liveUserRequestService.ensureUserAndEnqueue(trimmed);

        if (user.getStatus() == GitHubUserStatus.NOT_FOUND) {
            log.info("GET /u/{} not found", login);
            return redirectHome(HomeMessage.GITHUB_USER_NOT_FOUND, trimmed);
        }
        if (user.getStatus() == GitHubUserStatus.FAILED) {
            log.info("GET /u/{} failed", login);
            return redirectHome(HomeMessage.GITHUB_UNAVAILABLE, trimmed);
        }

        if (user.getStatus() == GitHubUserStatus.NEW) {
            log.info("GET /u/{} fetching", login);
            model.addAttribute("login", trimmed);
            return "user-fetching";
        }

        if (!user.getLogin().equals(trimmed)) {
            String loginToUse = user.getLogin();
            log.info("GET /u/{} redirect to {}", login, loginToUse);
            return "redirect:/u/" + loginToUse;
        }

        Optional<GitHubUserPhoto> photoOpt = this.gitHubUserPhotoRepository.findByGitHubUserId(user.getId());
        String photoDataUrl = photoOpt.map(this::asDataUrl).orElse(null);
        addRepositoryDerivedModel(user, model);
        addActivityModel(user, model);
        addRepoCommitChartModel(user, model);

        log.info("GET /u/{} success", login);

        model.addAttribute("login", user.getLogin());
        model.addAttribute("name", user.getName());
        model.addAttribute("bio", user.getBio());
        model.addAttribute("followers", user.getFollowers());
        model.addAttribute("fetchedAt", formatFetchedAt(user.getFetchedAt()));
        model.addAttribute("photoDataUrl", photoDataUrl);
        model.addAttribute("gitCloneDepth", this.gitConfig.getCloneDepth());
        return "user";
    }

    @GetMapping("/u/{login}/repositories")
    public String repositories(@PathVariable("login") String login, Model model) {
        String trimmed = login == null ? "" : login.trim();
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findByLoginLower(trimmed.toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            addEmptyRepositoryDerivedModel(model, GitHubRepositorySyncStatus.FAILED);
            model.addAttribute("followers", 0);
            return "fragments/repositories :: topRepositories";
        }

        GitHubUser user = userOpt.get();
        addRepositoryDerivedModel(user, model);
        model.addAttribute("followers", user.getFollowers());
        return "fragments/repositories :: topRepositories";
    }

    @GetMapping("/u/{login}/languages")
    public String languages(@PathVariable("login") String login, Model model) {
        String trimmed = login == null ? "" : login.trim();
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findByLoginLower(trimmed.toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            addEmptyRepositoryDerivedModel(model, GitHubRepositorySyncStatus.FAILED);
            return "fragments/languages :: languageChart";
        }

        addRepositoryDerivedModel(userOpt.get(), model);
        return "fragments/languages :: languageChart";
    }

    @GetMapping("/u/{login}/activity")
    public String activity(@PathVariable("login") String login, Model model) {
        String trimmed = login == null ? "" : login.trim();
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findByLoginLower(trimmed.toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            addEmptyActivityModel(model);
            return "fragments/git-activity :: gitActivity";
        }

        GitHubUser user = userOpt.get();
        addActivityModel(user, model);
        model.addAttribute("gitCloneDepth", this.gitConfig.getCloneDepth());
        return "fragments/git-activity :: gitActivity";
    }

    @GetMapping("/u/{login}/repo-commits")
    public String repoCommits(@PathVariable("login") String login, Model model) {
        String trimmed = login == null ? "" : login.trim();
        Optional<GitHubUser> userOpt = this.gitHubUserRepository.findByLoginLower(trimmed.toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            model.addAttribute("deepAnalysisStatus", GitDeepAnalysisStatus.FAILED.name());
            return "fragments/repo-commits :: repoCommitChart";
        }

        GitHubUser user = userOpt.get();
        addRepoCommitChartModel(user, model);
        model.addAttribute("deepAnalysisStatus", toString(user.getDeepAnalysisStatus()));
        return "fragments/repo-commits :: repoCommitChart";
    }

    @GetMapping("/u/{login}/status")
    @ResponseBody
    public ResponseEntity<UserStatusResponse> status(@PathVariable("login") String login) {
        String trimmed = login == null ? "" : login.trim();

        GitHubUser user = this.liveUserRequestService.ensureUserAndEnqueue(trimmed);
        if (user.getStatus() == GitHubUserStatus.NEW) {
            return ResponseEntity.ok(new UserStatusResponse(GitHubUserStatus.NEW, null, null, trimmed));
        }
        if (user.getStatus() == GitHubUserStatus.NOT_FOUND) {
            return ResponseEntity.ok(new UserStatusResponse(GitHubUserStatus.NOT_FOUND, HomeMessage.GITHUB_USER_NOT_FOUND.name(), null, trimmed));
        }
        if (user.getStatus() == GitHubUserStatus.FAILED) {
            return ResponseEntity.ok(new UserStatusResponse(GitHubUserStatus.FAILED, HomeMessage.GITHUB_UNAVAILABLE.name(), null, trimmed));
        }

        return ResponseEntity.ok(new UserStatusResponse(GitHubUserStatus.FETCHED, null, user.getLogin(), trimmed));
    }

    private String redirectHome(HomeMessage message, String login) {
        String encodedLogin = UriUtils.encodeQueryParam(login, UTF_8);
        return "redirect:/?" + Constants.MESSAGE_PARAM + "=" + message.name() + "&" + Constants.LOGIN_PARAM + "=" + encodedLogin;
    }

    private String asDataUrl(GitHubUserPhoto photo) {
        String contentType = photo.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String base64 = Base64.getEncoder().encodeToString(photo.getPhotoBlob());
        return "data:" + contentType + ";base64," + base64;
    }

    private void addRepositoryDerivedModel(GitHubUser user, Model model) {
        Long userId = user.getId();

        List<TopRepositoryView> topRepositories = this.gitHubRepositoryRepository
                .findTop5ByGitHubUser_IdOrderByStargazersCountDescNameAsc(userId)
                .stream()
                .map(repository -> TopRepositoryView.create(
                        repository.getName(),
                        repository.getHtmlUrl(),
                        repository.getStargazersCount(),
                        repository.getLanguage(),
                        repository.getPushedAt()
                ))
                .toList();

        LanguageChartView languageChart = LanguageChartView.fromCounts(this.gitHubRepositoryRepository
                .findLanguageCountsByGitHubUserId(userId)
                .stream()
                .map(count -> new LanguageChartView.LanguageCountInput(count.getLanguage(), count.getRepositoryCount()))
                .toList());

        GitHubRepositorySyncStatus repositorySyncStatus = user.getRepositorySyncStatus();
        model.addAttribute("topRepositories", topRepositories);
        model.addAttribute("languageChart", languageChart);
        model.addAttribute("repositorySyncState", repositorySyncStatus == null ? GitHubRepositorySyncStatus.NEW.name() : repositorySyncStatus.name());
        model.addAttribute("totalRepositories", this.gitHubRepositoryRepository.countByGitHubUser_Id(userId));
        model.addAttribute("totalStars", this.gitHubRepositoryRepository.sumStargazersByGitHubUserId(userId));
        model.addAttribute("totalForks", this.gitHubRepositoryRepository.sumForksByGitHubUserId(userId));
    }

    private void addEmptyRepositoryDerivedModel(Model model, GitHubRepositorySyncStatus repositorySyncStatus) {
        model.addAttribute("topRepositories", List.of());
        model.addAttribute("languageChart", LanguageChartView.empty());
        model.addAttribute("repositorySyncState", repositorySyncStatus.name());
    }

    private void addActivityModel(GitHubUser user, Model model) {
        Long userId = user.getId();
        GitDeepAnalysisStatus status = user.getDeepAnalysisStatus();

        if (status == GitDeepAnalysisStatus.COMPLETED) {
            List<GitHourlyActivity> hourlyActivities = this.gitHourlyActivityRepository.findByGitHubUser_IdOrderByHourAsc(userId);
            Map<Integer, Integer> countByHour = new HashMap<>();
            for (GitHourlyActivity a : hourlyActivities) {
                countByHour.put(a.getHour(), a.getCommitCount());
            }
            int maxHourly = hourlyActivities.stream().mapToInt(GitHourlyActivity::getCommitCount).max().orElse(0);
            List<HourlyActivityView> hourlyViews = new ArrayList<>();
            for (int hour = 0; hour < 24; hour++) {
                int count = countByHour.getOrDefault(hour, 0);
                hourlyViews.add(HourlyActivityView.from(hour, count, maxHourly));
            }

            List<GitMonthlyActivity> dbActivities = this.gitMonthlyActivityRepository.findByGitHubUser_IdOrderByActivityMonthAsc(userId);
            int maxMonthly = dbActivities.stream().mapToInt(GitMonthlyActivity::getCommitCount).max().orElse(0);

            Map<String, Integer> countByMonth = new HashMap<>();
            for (GitMonthlyActivity a : dbActivities) {
                countByMonth.put(a.getActivityMonth(), a.getCommitCount());
            }

            YearMonth now = YearMonth.now();
            YearMonth cutoff = now.minusMonths(11);
            List<MonthlyActivityView> monthlyViews = new ArrayList<>();
            for (YearMonth ym = cutoff; !ym.isAfter(now); ym = ym.plusMonths(1)) {
                String key = ym.toString();
                int count = countByMonth.getOrDefault(key, 0);
                monthlyViews.add(MonthlyActivityView.from(key, count, maxMonthly));
            }
            model.addAttribute("monthlyActivities", monthlyViews);
            model.addAttribute("hourlyActivities", hourlyViews);

            List<GitWeekdayActivity> weekdayActivities = this.gitWeekdayActivityRepository.findByGitHubUser_IdOrderByDayOfWeekAsc(userId);
            Map<Integer, Integer> countByDay = new HashMap<>();
            for (GitWeekdayActivity a : weekdayActivities) {
                countByDay.put(a.getDayOfWeek(), a.getCommitCount());
            }
            int maxWeekday = weekdayActivities.stream().mapToInt(GitWeekdayActivity::getCommitCount).max().orElse(0);
            List<WeekdayActivityView> weekdayViews = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                int count = countByDay.getOrDefault(day, 0);
                weekdayViews.add(WeekdayActivityView.from(day, count, maxWeekday));
            }
            model.addAttribute("weekdayActivities", weekdayViews);

            Optional<GitCommitAnalysis> analysisOpt = this.gitCommitAnalysisRepository.findByGitHubUser_Id(userId);
            analysisOpt.ifPresent(a -> {
                model.addAttribute("gitAnalysis", a);
                model.addAttribute("lastAnalyzedAt", formatFetchedAt(a.getAnalyzedAt()));
            });
        }

        model.addAttribute("deepAnalysisStatus", toString(status));
    }

    private void addRepoCommitChartModel(GitHubUser user, Model model) {
        if (user.getDeepAnalysisStatus() == GitDeepAnalysisStatus.COMPLETED) {
            List<GitHubRepository> topRepos = this.gitHubRepositoryRepository
                    .findTopByGitHubUser_IdOrderByCommitCountDescNameAsc(user.getId(), PageRequest.of(0, 8));
            List<RepoCommitInput> inputs = topRepos.stream()
                    .map(r -> new RepoCommitInput(r.getName(), r.getCommitCount()))
                    .toList();
            model.addAttribute("repoCommitChart", RepoCommitChartView.fromRepoCounts(inputs));
        }
    }

    private void addEmptyActivityModel(Model model) {
        model.addAttribute("deepAnalysisStatus", GitDeepAnalysisStatus.FAILED.name());
    }

    private static String toString(GitDeepAnalysisStatus status) {
        return status == null ? GitDeepAnalysisStatus.NOT_STARTED.name() : status.name();
    }

    private static String formatFetchedAt(Instant fetchedAt) {
        if (fetchedAt == null) {
            return null;
        }
        return FETCHED_AT_DATE_FORMATTER.format(fetchedAt) + "\n" + FETCHED_AT_TIME_FORMATTER.format(fetchedAt);
    }
}
