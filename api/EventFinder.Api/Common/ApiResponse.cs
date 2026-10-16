namespace EventFinder.Api.Common;

/// <summary>
/// Standard response envelope returned by every API endpoint (except /health).
/// </summary>
public class ApiResponse<T>
{
    public bool Success { get; set; }
    public string? Message { get; set; }
    public T? Data { get; set; }
    public Dictionary<string, string[]>? Errors { get; set; }

    public static ApiResponse<T> Ok(T data, string? message = null) =>
        new() { Success = true, Data = data, Message = message };

    public static ApiResponse<T> Fail(string message, Dictionary<string, string[]>? errors = null) =>
        new() { Success = false, Message = message, Errors = errors };
}

public class ApiError
{
    public static IResult Validation(Dictionary<string, string[]> errors) =>
        Results.Json(ApiResponse<object>.Fail("Validation failed.", errors), statusCode: 400);

    public static IResult Unauthorized(string message = "Unauthorized.") =>
        Results.Json(ApiResponse<object>.Fail(message), statusCode: 401);

    public static IResult Forbidden(string message = "You do not own this resource.") =>
        Results.Json(ApiResponse<object>.Fail(message), statusCode: 403);

    public static IResult NotFound(string message = "Not found.") =>
        Results.Json(ApiResponse<object>.Fail(message), statusCode: 404);

    public static IResult Conflict(string message) =>
        Results.Json(ApiResponse<object>.Fail(message), statusCode: 409);

    public static IResult BadRequest(string message, Dictionary<string, string[]>? errors = null) =>
        Results.Json(ApiResponse<object>.Fail(message, errors), statusCode: 400);
}
