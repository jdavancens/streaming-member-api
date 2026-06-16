import "@typespec/http";
import "@typespec/rest";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.Rest;

@service({title: "Member Auth API", version: "1.0.0"})
@server("http://localhost:8081", "Local development")
namespace MemberAuth;

model LoginRequest {
  email: string;
  password: string;
}

model TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: int32;
  tokenType: "Bearer";
}

model RefreshRequest {
  refreshToken: string;
}

model LogoutRequest {
  refreshToken: string;
}

@error
model UnauthorizedError {
  @statusCode statusCode: 401;
  message: string;
}

@error
model ValidationError {
  @statusCode statusCode: 400;
  message: string;
  fields?: Record<string[]>;
}

@route("/api/v1/auth")
interface Auth {
  @post
  @route("/login")
  @doc("Exchange credentials for JWT access and refresh tokens.")
  login(@body body: LoginRequest): TokenResponse | UnauthorizedError;

  @post
  @route("/refresh")
  @doc("Rotate the access token using a valid refresh token.")
  refresh(@body body: RefreshRequest): TokenResponse | UnauthorizedError;

  @post
  @route("/logout")
  @useAuth(BearerAuth)
  @doc("Invalidate the refresh token. Access token expires naturally.")
  logout(@body body: LogoutRequest): {
    @statusCode statusCode: 204;
  };
}
