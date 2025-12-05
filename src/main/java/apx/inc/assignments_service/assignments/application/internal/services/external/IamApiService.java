package apx.inc.assignments_service.assignments.application.internal.services.external;

import apx.inc.assignments_service.shared.infrastructure.http.dtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class IamApiService {

    private final RestTemplate restTemplate;

    @Value("${services.iam.url:http://localhost:8081}")
    private String iamBaseUrl;

    public IamApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String getCurrentToken() {
        try {
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes) {
                HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            System.out.println("❌ No se pudo obtener el token del request: " + e.getMessage());
        }
        return null;
    }

    public boolean userExists(Long userId) {
        try {
            String token = getCurrentToken();
            if (token == null) return false;

            String url = iamBaseUrl + "/api/v1/users/{userId}";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, UserResponse.class, userId
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.out.println("❌ Error validando usuario en IAM: " + e.getMessage());
            return false;
        }
    }

    public boolean isTeacher(Long userId) {
        try {
            String token = getCurrentToken();
            if (token == null) return false;

            String url = iamBaseUrl + "/api/v1/users/{userId}";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, UserResponse.class, userId
            );
            UserResponse user = response.getBody();
            return user != null && user.getRoles().contains("ROLE_TEACHER");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isStudent(Long userId) {
        try {
            String token = getCurrentToken();
            if (token == null) return false;

            String url = iamBaseUrl + "/api/v1/users/{userId}";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, UserResponse.class, userId
            );
            UserResponse user = response.getBody();
            return user != null && user.getRoles().contains("ROLE_STUDENT");
        } catch (Exception e) {
            return false;
        }
    }

    public UserResponse getCurrentUser() {
        try {
            String token = getCurrentToken();
            if (token == null) {
                throw new RuntimeException("No authentication token found");
            }

            String url = iamBaseUrl + "/api/v1/users/me";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, UserResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to get current user: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("❌ Error obteniendo usuario actual: " + e.getMessage());
            throw new RuntimeException("Failed to get current user: " + e.getMessage());
        }
    }
}