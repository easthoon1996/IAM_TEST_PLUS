package com.dreamsecurity.iam.controller;

import com.dreamsecurity.iam.config.RestTemplateConfig;
import com.dreamsecurity.iam.model.EmployeeDto;
import com.dreamsecurity.iam.model.EmployeeFormDto;
import com.dreamsecurity.iam.service.EmployeeTestService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EmployeeTestController {

    @Autowired
    private RestTemplate restTemplate; // RestTemplateConfig에서 만든 Bean을 사용!

    @Autowired
    private EmployeeTestService employeeTestService;

    @Value("${sap.mock.auth}")
    private String sapMockAuth;

    @Value("${sap.mock.base-url}")
    private String baseUrl;

    @Value("${sap.mock.employees-api}")
    private String employeesApi;

    @Value("${sap.mock.generate-api}")
    private String generateApi;

    @Value("${app.default-page-size}")
    private int defaultPageSize;

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/test-sap-api")
    public String testSapApi(
            @RequestParam(defaultValue = "-1") int size,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String filter, // 🔥 $filter 전체 문자열 받기
            Model model
    ) throws Exception {
        if (size == -1) {
            size = defaultPageSize;
        }

        int skip = (page - 1) * size;

        String apiUrl;
        if (filter != null && !filter.trim().isEmpty()) {
            // 🔥 $filter 전체 조건으로 검색
            apiUrl = String.format(baseUrl + employeesApi + "?$skip=%d&$top=%d&$filter=%s", skip, size, filter);
        } else {
            // 기본 조회
            apiUrl = String.format(baseUrl + employeesApi + "?$skip=%d&$top=%d", skip, size);
        }

        // 인증 헤더
        String base64Creds = Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + base64Creds);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            model.addAttribute("employeeList", List.of());
            model.addAttribute("notFound", true);
        } else {
            List<EmployeeDto> employeeList = employeeTestService.parseEmployeeResponse(response.getBody());
            model.addAttribute("employeeList", employeeList);
            model.addAttribute("notFound", false);
        }

        // 페이지 정보 + 검색어 추가
        model.addAttribute("currentPage", page);
        model.addAttribute("size", size);
        model.addAttribute("filter", filter); // 🔥 사용자가 입력한 $filter 조건

        return "sap-api-test";
    }



    @GetMapping("/add")
    public String addEmployeeForm(Model model) {
        model.addAttribute("employeeForm", new EmployeeFormDto());
        return "add-employee";
    }

    @GetMapping("/generate-employees")
    public String generateEmployees(
            @RequestParam(name = "count", defaultValue = "20") int count, // 사용자로부터 개수 입력 받기
            Model model
    ) throws Exception {
        String apiUrl = baseUrl + generateApi +"?count=" + count;

        // Basic Auth 헤더
        String base64Creds = Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + base64Creds);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

        return "redirect:/test-sap-api";
    }

    @PostMapping("/add-employee")
    public String addEmployee(
            @ModelAttribute EmployeeFormDto employeeForm,
            RedirectAttributes redirectAttributes
    ) {
        String sapMockUrl = baseUrl + employeesApi;

        try {
            String base64Creds = Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + base64Creds);
            headers.setContentType(MediaType.APPLICATION_JSON); // ✅ JSON 요청 명시

            HttpEntity<EmployeeFormDto> request = new HttpEntity<>(employeeForm, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(sapMockUrl, request, String.class);

            // 응답 로깅
            System.out.println("응답 상태코드: " + response.getStatusCode());
            System.out.println("응답 본문: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                redirectAttributes.addFlashAttribute("message", "사용자가 추가되었습니다!");
            } else {
                redirectAttributes.addFlashAttribute("error", "사용자 추가 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "오류: " + e.getMessage());
        }

        return "redirect:/test-sap-api";
    }

    /*@GetMapping("/employee-detail")
    @ResponseBody
    public ResponseEntity<?> getEmployeeDetail(@RequestParam String employeeId) {
        // 🔥 SAP Mock API 엔드포인트 (예: /Employees/{employeeId})
        String apiUrl = baseUrl + employeesApi + "/" + employeeId;

        String base64Creds = Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + base64Creds);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                // 🔥 JSON 그대로 응답 (프론트가 보기 좋게 파싱)
                ObjectMapper mapper = new ObjectMapper();
                Object json = mapper.readValue(response.getBody(), Object.class);
                return ResponseEntity.ok(json);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                        .body("직원 상세정보 조회 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류: " + e.getMessage());
        }
    }*/

    @GetMapping("/employee-detail")
    @ResponseBody
    public ResponseEntity<?> getEmployeeDetail(@RequestParam String employeeId) {
        String apiUrl = baseUrl + employeesApi + "/" + employeeId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> json = mapper.readValue(response.getBody(), Map.class);
                return ResponseEntity.ok(json);
            } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", Map.of("code", "NotFound", "message", "Employee not found"));
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            } else {
                return ResponseEntity.status(response.getStatusCode()).body("직원 상세정보 조회 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류: " + e.getMessage());
        }
    }

    @GetMapping("/employee-roles")
    @ResponseBody
    public ResponseEntity<?> getEmployeeRoles(@RequestParam String employeeId) {
        String apiUrl = baseUrl + employeesApi + "/" + employeeId + "/Roles";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> json = mapper.readValue(response.getBody(), Map.class);
                return ResponseEntity.ok(json);
            } else {
                return ResponseEntity.status(response.getStatusCode()).body("역할 조회 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류: " + e.getMessage());
        }
    }

    @GetMapping("/employee-privileges")
    @ResponseBody
    public ResponseEntity<?> getEmployeePrivileges(@RequestParam String employeeId) {
        String apiUrl = baseUrl + employeesApi + "/" + employeeId + "/Privileges";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(sapMockAuth.getBytes(StandardCharsets.UTF_8)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> json = mapper.readValue(response.getBody(), Map.class);
                return ResponseEntity.ok(json);
            } else {
                return ResponseEntity.status(response.getStatusCode()).body("권한 조회 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("오류: " + e.getMessage());
        }
    }


    // 권한 조회
    @GetMapping("/check-authorization")
    @ResponseBody
    public ResponseEntity<?> checkAuth(
            @RequestParam String employeeId,
            @RequestParam String object,
            @RequestParam String field,
            @RequestParam String value) {

        String url = String.format("%s%s/%s/CheckAuthorization?object=%s&field=%s&value=%s",
                baseUrl, employeesApi, employeeId, object, field, value);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(sapMockAuth.getBytes()));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = resp.getBody();
                // OData2 성공 응답: body가 이미 {d: ...} 구조면 그대로, 아니면 감싸기
                if (body != null && body.containsKey("d")) {
                    return ResponseEntity.ok(body);
                } else {
                    Map<String, Object> odata = new HashMap<>();
                    odata.put("d", body);
                    return ResponseEntity.ok(odata);
                }
            } else if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
                // OData2 에러 구조
                Map<String, Object> error = new HashMap<>();
                Map<String, String> detail = new HashMap<>();
                detail.put("code", "NotFound");
                detail.put("message", "직원이 존재하지 않음");
                error.put("error", detail);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            } else {
                return ResponseEntity.status(resp.getStatusCode())
                        .body("권한 조회 실패: " + resp.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            Map<String, String> detail = new HashMap<>();
            detail.put("code", "Exception");
            detail.put("message", e.getMessage());
            error.put("error", detail);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }


}