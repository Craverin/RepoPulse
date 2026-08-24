type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE"

interface RequestOptions {
  method?: HttpMethod
  body?: unknown
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body = null } = options
  const headers: Record<string, string> = {}

  if (body !== null) headers["Content-Type"] = "application/json"

  const response = await fetch(`${path}`, {
    method,
    headers,
    credentials: "include",
    body: body !== null ? JSON.stringify(body) : undefined
  })

  if (!response.ok) {
    const errorBody: unknown = await response.json().catch(() => null)

    let message = `Request failed: ${response.status} ${response.statusText}`

    if (
      errorBody !== null &&
      typeof errorBody === "object" &&
      "message" in errorBody &&
      typeof errorBody.message === "string"
    ) {
      message = errorBody.message
    }

    throw new Error(message)
  }

  return response.json() as Promise<T>
}
