# AI PR Copilot

A self-hosted AI-powered code audit and pull request analysis service with multi-provider support. This REST API service
analyzes Git diffs using state-of-the-art language models to provide structured code reviews, identify potential risks,
and suggest test cases.

[![CI](https://github.com/kxng0109/AI-PR-Copilot/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kxng0109/AI-PR-Copilot/actions/workflows/ci.yml)

> **Note:** This project is actively under development. Features and APIs may change as the project evolves.

## Table of Contents

- [Features](#features)
- [Supported AI Providers](#supported-ai-providers)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
    - [Clone the Repository](#clone-the-repository)
    - [Configuration](#configuration)
    - [Running the Application](#running-the-application)
- [API Documentation](#api-documentation)
    - [Analyze Diff Endpoint](#analyze-diff-endpoint)
    - [Example Request](#example-request)
    - [Example Response](#example-response)
- [Configuration Reference](#configuration-reference)
    - [AI Provider Configuration](#ai-provider-configuration)
    - [Analysis Settings](#analysis-settings)
    - [Fallback Configuration](#fallback-configuration)
- [Provider Setup Guides](#provider-setup-guides)
    - [OpenAI](#openai)
    - [Anthropic Claude](#anthropic-claude)
    - [Google Gemini](#google-gemini)
    - [Ollama](#ollama)
- [Error Handling](#error-handling)
- [Health Checks](#health-checks)
- [Testing](#testing)
- [Architecture Overview](#architecture-overview)
- [License](#license)

## Features

- **Multi-Provider AI Support**: Seamlessly switch between OpenAI, Anthropic Claude, Google Gemini, and Ollama
- **Automatic Fallback**: Configure a fallback AI provider for high availability
- **Structured Code Analysis**: Receive detailed breakdowns including title, summary, risks, and suggested tests
- **Flexible Configuration**: Customize analysis style, language, temperature, and token limits
- **Comprehensive Error Handling**: Graceful degradation with detailed error messages
- **Production Ready**: Includes health checks, metrics, and extensive test coverage
- **OpenAPI Documentation**: Interactive API documentation via Swagger UI
- **Startup Validation**: Ensures all required API keys and configurations are present before launch

## Supported AI Providers

| Provider          | Models                                       | Authentication               |
|-------------------|----------------------------------------------|------------------------------|
| **OpenAI**        | GPT-4o, GPT-4o-mini, GPT-3.5-turbo           | API Key                      |
| **Anthropic**     | Claude Sonnet 4, Claude Opus 4               | API Key                      |
| **Google Gemini** | Gemini 2.0 Flash, Gemini Pro                 | GCP Project ID + Credentials |
| **Ollama**        | Any local model (Qwen, Llama, Mistral, etc.) | Local installation           |

## Prerequisites

- Java 25 or higher
- Maven 3.6 or higher
- An API key for at least one AI provider (or a running Ollama instance for local models)

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/kxng0109/ai-pr-copilot.git
cd ai-pr-copilot
```

### Configuration

1. Copy the example environment file:

```bash
cp .env.example .env
```

2. Edit the `.env` file and configure your preferred AI provider. At minimum, you need to set:

```bash
# Choose your primary provider
PRCOPILOT_AI_PROVIDER=openai

# Add the corresponding API key
OPENAI_API_KEY=sk-your-openai-key-here
```

See the [Provider Setup Guides](#provider-setup-guides) section for detailed configuration instructions for each
provider.

### Running the Application

Build and run the application using Maven:

```bash
mvn clean install
mvn spring-boot:run
```

The service will start on `http://localhost:8080` by default.

Verify the service is running:

```bash
curl http://localhost:8080/actuator/health
```

## API Documentation

Once the application is running, you can access the interactive Swagger UI documentation at:

```
http://localhost:8080/swagger-ui.html
```

The OpenAPI specification is available at:

```
http://localhost:8080/api-docs
```

### Analyze Diff Endpoint

**Endpoint:** `POST /api/v1/analyze-diff`

**Content Type:** `application/json`

### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/analyze-diff \
  -H "Content-Type: application/json" \
  -d '{
    "diff": "diff --git a/src/main/UserService.java b/src/main/UserService.java\nindex abc123..def456 100644\n--- a/src/main/UserService.java\n+++ b/src/main/UserService.java\n@@ -10,7 +10,7 @@ public class UserService {\n-    public User getUser(String id) {\n-        return database.findById(id);\n+    public User getUser(String id) throws UserNotFoundException {\n+        return database.findById(id).orElseThrow(() -> new UserNotFoundException(id));\n     }\n }",
    "language": "en",
    "style": "conventional-commits",
    "maxSummaryLength": 500,
    "requestId": "req-12345"
  }'
```

### Example Response

```json
{
	"title": "refactor: improve error handling in UserService",
	"summary": "Modified getUser method to throw UserNotFoundException when user is not found instead of returning null.",
	"details": "The getUser method signature now includes a throws clause for UserNotFoundException. The implementation uses Optional.orElseThrow() to raise an exception when the database query returns empty, replacing the previous behavior of returning null.",
	"risks": [
		"Breaking change: existing callers must now handle UserNotFoundException",
		"No null checks are present in the diff; ensure all callers are updated",
		"UserNotFoundException is not defined in this diff; verify it exists in the codebase"
	],
	"suggestedTests": [
		"Test that getUser throws UserNotFoundException when user does not exist",
		"Test that getUser returns valid User object when user exists",
		"Integration test to verify exception propagates correctly through call stack"
	],
	"touchedFiles": [
		"src/main/UserService.java"
	],
	"analysisNotes": "This is a common refactoring pattern to improve error handling. Ensure backward compatibility is considered if this is a public API.",
	"metadata": {
		"modelName": "gpt-4o",
		"provider": "openai",
		"modelLatencyMs": 1247,
		"tokensUsed": 312
	},
	"requestId": "req-12345",
	"rawModelOutput": null
}
```

**Request Parameters:**

| Field              | Type    | Required | Description                                      |
|--------------------|---------|----------|--------------------------------------------------|
| `diff`             | string  | Yes      | The Git diff content in unified diff format      |
| `language`         | string  | No       | Language for the analysis (default: `en`)        |
| `style`            | string  | No       | Analysis style (default: `conventional-commits`) |
| `maxSummaryLength` | integer | No       | Maximum length for the summary                   |
| `requestId`        | string  | No       | Unique identifier for request tracking           |

**Response Fields:**

| Field            | Type   | Description                                        |
|------------------|--------|----------------------------------------------------|
| `title`          | string | Short technical title of the change                |
| `summary`        | string | Concise description of what changed                |
| `details`        | string | Detailed technical breakdown of the implementation |
| `risks`          | array  | Specific risks or issues introduced by the changes |
| `suggestedTests` | array  | Recommended test cases to verify the changes       |
| `touchedFiles`   | array  | List of files modified in the diff                 |
| `analysisNotes`  | string | Additional analysis notes or caveats               |
| `metadata`       | object | Information about the AI model call                |
| `requestId`      | string | Echo of the request ID if provided                 |
| `rawModelOutput` | string | Raw model output (only if enabled in config)       |

## Configuration Reference

All configuration can be set via environment variables or in the `application.yml` file. Refer to `.env.example` for a
complete list of available settings.

### AI Provider Configuration

```bash
# Select your primary AI provider
PRCOPILOT_AI_PROVIDER=openai

# Common AI settings (applied to all providers)
AI_TEMPERATURE=0.1
AI_MAX_TOKENS=1024
AI_TIMEOUT_MILLIS=30000
```

### Analysis Settings

```bash
# Maximum diff size in characters
PRCOPILOT_ANALYSIS_MAX_DIFF_CHARS=50000

# Default language and style
PRCOPILOT_ANALYSIS_DEFAULT_LANGUAGE=en
PRCOPILOT_ANALYSIS_DEFAULT_STYLE=conventional-commits

# Include raw model output in response (useful for debugging)
PRCOPILOT_ANALYSIS_INCLUDE_RAW_MODEL_OUTPUT=false
```

### Fallback Configuration

Enable automatic fallback to a secondary provider if the primary fails:

```bash
PRCOPILOT_AI_AUTO_FALLBACK=true
PRCOPILOT_AI_FALLBACK_PROVIDER=anthropic

# Configure both provider credentials
OPENAI_API_KEY=sk-your-openai-key
ANTHROPIC_API_KEY=sk-ant-your-anthropic-key
```

## Provider Setup Guides

### OpenAI

1. Get an API key from [OpenAI Platform](https://platform.openai.com/api-keys)
2. Configure in `.env`:

```bash
PRCOPILOT_AI_PROVIDER=openai
OPENAI_API_KEY=sk-your-openai-key-here
OPENAI_MODEL=gpt-4o
```

### Anthropic Claude

1. Get an API key from [Anthropic Console](https://console.anthropic.com/)
2. Configure in `.env`:

```bash
PRCOPILOT_AI_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-your-anthropic-key-here
ANTHROPIC_MODEL=claude-sonnet-4-0
```

### Google Gemini

1. Set up a Google Cloud Project
2. Enable the Vertex AI API
3. Configure authentication using application default credentials or service account
4. Configure in `.env`:

```bash
PRCOPILOT_AI_PROVIDER=gemini
GEMINI_PROJECT_ID=your-gcp-project-id
GEMINI_LOCATION=us-central1
GEMINI_MODEL=gemini-2.0-flash
```

For detailed GCP setup instructions, refer to
the [Google Cloud Vertex AI documentation](https://cloud.google.com/vertex-ai/docs).

### Ollama

1. Install Ollama locally or run via Docker:

```bash
docker run -d -p 11434:11434 ollama/ollama
```

2. Pull a model:

```bash
ollama pull qwen3:4b
```

3. Configure in `.env`:

```bash
PRCOPILOT_AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:4b
```

## Error Handling

The API provides structured error responses for various scenarios:

**400 Bad Request** - Invalid request parameters:

```json
{
	"timestamp": "2024-12-01T10:30:00Z",
	"statusCode": 400,
	"error": "Bad Request",
	"message": "{diff=Diff must not be blank}",
	"path": "/api/v1/analyze-diff"
}
```

**413 Payload Too Large** - Diff exceeds maximum size:

```json
{
	"timestamp": "2024-12-01T10:30:00Z",
	"statusCode": 413,
	"error": "Payload Too Large",
	"message": "Diff exceeded maximum allowed size of 50000 characters",
	"path": "/api/v1/analyze-diff"
}
```

**422 Unprocessable Entity** - AI model returned invalid output:

```json
{
	"timestamp": "2024-12-01T10:30:00Z",
	"statusCode": 422,
	"error": "Unprocessable Entity",
	"message": "Model returned invalid JSON output",
	"path": "/api/v1/analyze-diff"
}
```

**500 Internal Server Error** - Unexpected server error:

```json
{
	"timestamp": "2024-12-01T10:30:00Z",
	"statusCode": 500,
	"error": "Internal Server Error",
	"message": "Could not process diff analysis due to internal error",
	"path": "/api/v1/analyze-diff"
}
```

**504 Gateway Timeout** - AI provider request timeout:

```json
{
	"timestamp": "2024-12-01T10:30:00Z",
	"statusCode": 504,
	"error": "Gateway Timeout",
	"message": "AI Model request timed out",
	"path": "/api/v1/analyze-diff"
}
```

## Health Checks

The service exposes Spring Boot Actuator endpoints for monitoring:

**Health Check:**

```bash
curl http://localhost:8080/actuator/health
```

**Application Info:**

```bash
curl http://localhost:8080/actuator/info
```

## Testing

The project includes comprehensive unit and integration tests. Run the test suite with:

```bash
mvn test
```

For integration tests with specific providers, ensure the corresponding credentials are configured in
`src/test/resources/application.yml`.

## Architecture Overview

The application follows a layered architecture:

- **Controller Layer** (`DiffAnalysisController`): Handles HTTP requests and responses
- **Service Layer**:
    - `DiffAnalysisService`: Orchestrates the diff analysis workflow
    - `AiChatService`: Manages AI model communication with timeout and error handling
    - `PromptBuilderService`: Constructs structured prompts for AI models
    - `DiffResponseMapperService`: Parses and maps AI responses to structured DTOs
- **Configuration Layer**: Multi-provider configuration with startup validation
- **Error Handling**: Global exception handler for consistent error responses

The service uses Spring AI to abstract provider-specific implementations, allowing seamless switching between different
AI models.

## License

This project is under active development. License information will be added in a future release.

---

**Project Status:** Active Development

For questions, issues, or feature requests, please open an issue on the GitHub repository.