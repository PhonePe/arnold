package com.phonepe.arnold.models.integration;

/**
 * @param authorizationUrl where the user authorizes the Arnold GitHub App to act on their behalf
 * @param installationUrl  where the app is installed on organizations or repositories; null when not configured
 */
public record GitHubAuthorizationResponse(String authorizationUrl, String installationUrl) {
}
