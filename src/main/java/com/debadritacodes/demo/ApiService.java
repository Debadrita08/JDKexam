package com.debadritacodes.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ApiService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String GENERATE_WEBHOOK_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
    
    public void executeOnStartup() {
        try {
            System.out.println("Starting API interaction...");
            
            // 1. Prepare and send initial request
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", "John Doe");
            requestBody.put("regNo", "REG12347");
            requestBody.put("email", "john@example.com");
            
            System.out.println("Sending POST request to: " + GENERATE_WEBHOOK_URL);
            System.out.println("Request body: " + requestBody);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                GENERATE_WEBHOOK_URL, 
                requestBody, 
                Map.class
            );
            
            // 2. Process response
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                System.err.println("Failed to get valid response. Status: " + response.getStatusCode());
                return;
            }
            
            Map<String, Object> responseBody = response.getBody();
            System.out.println("Received response: " + responseBody);
            
            // 3. Extract required fields with validation
            String webhookUrl = getStringFromMap(responseBody, "webhook");
            String accessToken = getStringFromMap(responseBody, "accessToken");
            Map<String, Object> data = getMapFromMap(responseBody, "data");
            
            if (webhookUrl == null || accessToken == null || data == null) {
                System.err.println("Missing required fields in response");
                return;
            }
            
            // 4. Determine which question to solve
            String regNo = "REG12347";
            int lastTwoDigits = Integer.parseInt(regNo.substring(regNo.length() - 2));
            boolean isOdd = lastTwoDigits % 2 != 0;
            
            Object outcome;
            if (isOdd) {
                System.out.println("Solving Question 1 (Mutual Followers)");
                outcome = solveQuestion1(data);
            } else {
                System.out.println("Solving Question 2 (Nth-Level Followers)");
                outcome = solveQuestion2(data);
            }
            
            // 5. Prepare and send result
            Map<String, Object> result = new HashMap<>();
            result.put("regNo", regNo);
            result.put("outcome", outcome);
            
            System.out.println("Prepared solution: " + result);
            sendToWebhookWithRetry(webhookUrl, accessToken, result, 4);
            
        } catch (Exception e) {
            System.err.println("Error in executeOnStartup: ");
            e.printStackTrace();
        }
    }
    
    private Object solveQuestion1(Map<String, Object> data) {
        try {
            System.out.println("Processing Question 1 with data: " + data);
            
            // Extract users list with flexible handling
            List<Map<String, Object>> users = extractUsersList(data);
            if (users == null || users.isEmpty()) {
                System.err.println("No valid users data found");
                return Collections.emptyList();
            }
            
            Set<List<Integer>> mutualPairs = new HashSet<>();
            
            for (Map<String, Object> user : users) {
                Integer id = getIntegerFromMap(user, "id");
                List<Integer> follows = getIntegerListFromMap(user, "follows");
                
                if (id == null || follows == null) continue;
                
                for (Integer followedId : follows) {
                    Map<String, Object> followedUser = findUserById(users, followedId);
                    if (followedUser != null) {
                        List<Integer> followedUserFollows = getIntegerListFromMap(followedUser, "follows");
                        if (followedUserFollows != null && followedUserFollows.contains(id)) {
                            mutualPairs.add(Arrays.asList(
                                Math.min(id, followedId),
                                Math.max(id, followedId)
                            ));
                        }
                    }
                }
            }
            
            // Sort the results
            List<List<Integer>> result = new ArrayList<>(mutualPairs);
            result.sort((a, b) -> {
                if (a.get(0).equals(b.get(0))) {
                    return a.get(1).compareTo(b.get(1));
                }
                return a.get(0).compareTo(b.get(0));
            });
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error in solveQuestion1: ");
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    private Object solveQuestion2(Map<String, Object> data) {
        try {
            System.out.println("Processing Question 2 with data: " + data);
            
            // Extract users map with flexible handling
            Map<String, Object> usersMap = data.containsKey("users") ? 
                (Map<String, Object>) data.get("users") : data;
            
            Integer n = getIntegerFromMap(usersMap, "n");
            Integer findId = getIntegerFromMap(usersMap, "findId");
            List<Map<String, Object>> users = extractUsersList(usersMap);
            
            if (n == null || findId == null || users == null) {
                System.err.println("Missing required parameters for Question 2");
                return Collections.emptyList();
            }
            
            Set<Integer> currentLevel = new HashSet<>();
            currentLevel.add(findId);
            
            for (int i = 0; i < n; i++) {
                Set<Integer> nextLevel = new HashSet<>();
                for (Integer userId : currentLevel) {
                    Map<String, Object> user = findUserById(users, userId);
                    if (user != null) {
                        List<Integer> follows = getIntegerListFromMap(user, "follows");
                        if (follows != null) {
                            nextLevel.addAll(follows);
                        }
                    }
                }
                currentLevel = nextLevel;
                if (currentLevel.isEmpty()) break;
            }
            
            return new ArrayList<>(currentLevel);
            
        } catch (Exception e) {
            System.err.println("Error in solveQuestion2: ");
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    // Helper methods
    private List<Map<String, Object>> extractUsersList(Map<String, Object> data) {
        Object usersObject = data.get("users");
        if (usersObject instanceof List) {
            return (List<Map<String, Object>>) usersObject;
        } else if (usersObject instanceof Map) {
            Map<String, Object> usersMap = (Map<String, Object>) usersObject;
            Object nestedUsers = usersMap.get("users");
            if (nestedUsers instanceof List) {
                return (List<Map<String, Object>>) nestedUsers;
            }
        }
        return null;
    }
    
    private Map<String, Object> findUserById(List<Map<String, Object>> users, Integer id) {
        if (users == null || id == null) return null;
        return users.stream()
            .filter(user -> id.equals(getIntegerFromMap(user, "id")))
            .findFirst()
            .orElse(null);
    }
    
    private void sendToWebhookWithRetry(String webhookUrl, String accessToken, 
                                      Map<String, Object> result, int maxRetries) {
        if (webhookUrl == null || accessToken == null) {
            System.err.println("Invalid webhook URL or access token");
            return;
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(result, headers);
        
        int attempts = 0;
        boolean success = false;
        
        while (attempts < maxRetries && !success) {
            attempts++;
            try {
                System.out.println("Attempt " + attempts + ": Sending to webhook...");
                ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    success = true;
                    System.out.println("Webhook call succeeded");
                } else {
                    System.err.println("Webhook call failed with status: " + response.getStatusCode());
                }
            } catch (Exception e) {
                System.err.println("Webhook call failed: " + e.getMessage());
            }
            
            if (!success && attempts < maxRetries) {
                try {
                    Thread.sleep(1000 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        if (!success) {
            System.err.println("Failed to call webhook after " + maxRetries + " attempts");
        }
    }
    
    // Safe type conversion helpers
    private String getStringFromMap(Map<String, Object> map, String key) {
        return map.containsKey(key) && map.get(key) instanceof String ? 
            (String) map.get(key) : null;
    }
    
    private Integer getIntegerFromMap(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<Integer> getIntegerListFromMap(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        Object value = map.get(key);
        if (value instanceof List) {
            try {
                return (List<Integer>) value;
            } catch (ClassCastException e) {
                return null;
            }
        }
        return null;
    }
    
    private Map<String, Object> getMapFromMap(Map<String, Object> map, String key) {
        return map.containsKey(key) && map.get(key) instanceof Map ? 
            (Map<String, Object>) map.get(key) : null;
    }
}