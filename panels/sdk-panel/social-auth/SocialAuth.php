<?php
declare(strict_types=1);

final class SocialAuthException extends RuntimeException
{
    public string $authCode;

    public function __construct(string $authCode, string $message)
    {
        parent::__construct($message);
        $this->authCode = $authCode;
    }
}

function social_json_input(int $maxBytes = 16384): array
{
    $raw = file_get_contents('php://input', false, null, 0, $maxBytes + 1);
    if (!is_string($raw) || $raw === '' || strlen($raw) > $maxBytes) {
        throw new SocialAuthException('INVALID_REQUEST', 'Invalid request body.');
    }
    try {
        $data = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
    } catch (Throwable $error) {
        throw new SocialAuthException('INVALID_JSON', 'Request body must be valid JSON.');
    }
    if (!is_array($data)) {
        throw new SocialAuthException('INVALID_REQUEST', 'Invalid request object.');
    }
    return $data;
}

function social_json_send(array $payload, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store, max-age=0');
    header('Pragma: no-cache');
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    exit;
}

function social_enabled(): bool
{
    return panel_config('SOCIAL_AUTH_ENABLED', false) === true;
}

function social_base_url(): string
{
    $base = rtrim(trim((string) panel_config('SOCIAL_AUTH_BASE_URL', '')), '/');
    if ($base === '' || filter_var($base, FILTER_VALIDATE_URL) === false
        || strtolower((string) parse_url($base, PHP_URL_SCHEME)) !== 'https') {
        throw new SocialAuthException('SERVER_CONFIGURATION_ERROR', 'Social auth base URL is not configured.');
    }
    return $base;
}

function social_provider_config(string $provider): array
{
    $provider = strtolower($provider);
    $configs = [
        'google' => [
            'client_id' => trim((string) panel_config('GOOGLE_OAUTH_CLIENT_ID', '')),
            'client_secret' => trim((string) panel_config('GOOGLE_OAUTH_CLIENT_SECRET', '')),
            'authorize_url' => 'https://accounts.google.com/o/oauth2/v2/auth',
            'token_url' => 'https://oauth2.googleapis.com/token',
        ],
        'facebook' => [
            'client_id' => trim((string) panel_config('FACEBOOK_APP_ID', '')),
            'client_secret' => trim((string) panel_config('FACEBOOK_APP_SECRET', '')),
            'authorize_url' => 'https://www.facebook.com/dialog/oauth',
            'token_url' => 'https://graph.facebook.com/oauth/access_token',
        ],
        'x' => [
            'client_id' => trim((string) panel_config('X_OAUTH_CLIENT_ID', '')),
            'client_secret' => trim((string) panel_config('X_OAUTH_CLIENT_SECRET', '')),
            'authorize_url' => 'https://x.com/i/oauth2/authorize',
            'token_url' => 'https://api.x.com/2/oauth2/token',
        ],
    ];
    if (!isset($configs[$provider])) {
        throw new SocialAuthException('INVALID_PROVIDER', 'Unsupported login provider.');
    }
    $config = $configs[$provider];
    if ($config['client_id'] === '') {
        throw new SocialAuthException('PROVIDER_NOT_CONFIGURED', ucfirst($provider) . ' login is not configured.');
    }
    if (in_array($provider, ['google', 'facebook'], true) && $config['client_secret'] === '') {
        throw new SocialAuthException('PROVIDER_NOT_CONFIGURED', ucfirst($provider) . ' login secret is not configured.');
    }
    return $config;
}

function social_redirect_uri(): string
{
    return social_base_url() . '/callback.php';
}

function social_expected_return_uri(string $packageName): string
{
    return strtolower($packageName) . '.parallaxsdk://social-auth/callback';
}

function social_validate_client_identity(array $payload): array
{
    $packageName = trim((string) ($payload['package_name'] ?? ''));
    $deviceId = trim((string) ($payload['device_id'] ?? ''));
    $installNonce = trim((string) ($payload['install_nonce'] ?? ''));
    if (preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,190}$/D', $packageName) !== 1
        || preg_match('/^[A-Za-z0-9_-]{22,128}$/D', $deviceId) !== 1
        || preg_match('/^[A-Za-z0-9_-]{43}$/D', $installNonce) !== 1) {
        throw new SocialAuthException('INVALID_CLIENT_IDENTITY', 'Invalid SDK client identity.');
    }
    return [$packageName, $deviceId, $installNonce];
}

function social_b64url(string $binary): string
{
    return rtrim(strtr(base64_encode($binary), '+/', '-_'), '=');
}

function social_random_token(int $bytes = 32): string
{
    return social_b64url(random_bytes($bytes));
}

function social_build_authorization_url(string $provider, string $state, ?string $codeVerifier): string
{
    $config = social_provider_config($provider);
    $redirect = social_redirect_uri();
    if ($provider === 'google') {
        $params = [
            'client_id' => $config['client_id'],
            'redirect_uri' => $redirect,
            'response_type' => 'code',
            'scope' => 'openid email profile',
            'state' => $state,
            'prompt' => 'select_account',
            'include_granted_scopes' => 'true',
            'access_type' => 'online',
        ];
    } elseif ($provider === 'facebook') {
        $params = [
            'client_id' => $config['client_id'],
            'redirect_uri' => $redirect,
            'response_type' => 'code',
            'scope' => 'public_profile,email',
            'state' => $state,
        ];
    } else {
        if ($codeVerifier === null || $codeVerifier === '') {
            throw new SocialAuthException('SERVER_CONFIGURATION_ERROR', 'X PKCE verifier is missing.');
        }
        $challenge = social_b64url(hash('sha256', $codeVerifier, true));
        $params = [
            'response_type' => 'code',
            'client_id' => $config['client_id'],
            'redirect_uri' => $redirect,
            'scope' => 'users.read',
            'state' => $state,
            'code_challenge' => $challenge,
            'code_challenge_method' => 'S256',
        ];
    }
    return $config['authorize_url'] . '?' . http_build_query($params, '', '&', PHP_QUERY_RFC3986);
}

function social_http(string $method, string $url, array $form = [], array $headers = []): array
{
    if (!function_exists('curl_init')) {
        throw new SocialAuthException('SERVER_CONFIGURATION_ERROR', 'PHP cURL extension is required for social login.');
    }
    if (strtolower((string) parse_url($url, PHP_URL_SCHEME)) !== 'https') {
        throw new SocialAuthException('HTTPS_REQUIRED', 'Provider endpoint must use HTTPS.');
    }
    $curl = curl_init($url);
    if ($curl === false) {
        throw new SocialAuthException('PROVIDER_CONNECTION_FAILED', 'Unable to initialize provider request.');
    }
    $options = [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_FOLLOWLOCATION => false,
        CURLOPT_MAXREDIRS => 0,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_HTTPHEADER => array_merge(['Accept: application/json'], $headers),
        CURLOPT_USERAGENT => 'Parallax-SDK-SocialAuth/1.0',
    ];
    if (defined('CURLOPT_PROTOCOLS') && defined('CURLPROTO_HTTPS')) {
        $options[CURLOPT_PROTOCOLS] = CURLPROTO_HTTPS;
    }
    if (strtoupper($method) === 'POST') {
        $options[CURLOPT_POST] = true;
        $options[CURLOPT_POSTFIELDS] = http_build_query($form, '', '&', PHP_QUERY_RFC3986);
        $options[CURLOPT_HTTPHEADER] = array_merge(
            ['Accept: application/json', 'Content-Type: application/x-www-form-urlencoded'],
            $headers
        );
    }
    curl_setopt_array($curl, $options);
    $body = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $error = curl_error($curl);
    curl_close($curl);
    if (!is_string($body) || $body === '') {
        throw new SocialAuthException('PROVIDER_CONNECTION_FAILED', $error !== '' ? 'Provider connection failed.' : 'Provider returned an empty response.');
    }
    if (strlen($body) > 1024 * 1024) {
        throw new SocialAuthException('PROVIDER_RESPONSE_INVALID', 'Provider response was too large.');
    }
    try {
        $json = json_decode($body, true, 64, JSON_THROW_ON_ERROR);
    } catch (Throwable $error) {
        throw new SocialAuthException('PROVIDER_RESPONSE_INVALID', 'Provider returned invalid JSON.');
    }
    if (!is_array($json)) {
        throw new SocialAuthException('PROVIDER_RESPONSE_INVALID', 'Provider returned an invalid response.');
    }
    if ($status < 200 || $status >= 300) {
        throw new SocialAuthException('PROVIDER_REJECTED', 'Provider rejected the authentication request.');
    }
    return $json;
}

function social_exchange_code(string $provider, string $code, ?string $codeVerifier): string
{
    $config = social_provider_config($provider);
    $redirect = social_redirect_uri();
    if ($provider === 'google') {
        $response = social_http('POST', $config['token_url'], [
            'code' => $code,
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'redirect_uri' => $redirect,
            'grant_type' => 'authorization_code',
        ]);
    } elseif ($provider === 'facebook') {
        $response = social_http('POST', $config['token_url'], [
            'code' => $code,
            'client_id' => $config['client_id'],
            'client_secret' => $config['client_secret'],
            'redirect_uri' => $redirect,
        ]);
    } else {
        if ($codeVerifier === null || $codeVerifier === '') {
            throw new SocialAuthException('PROVIDER_EXCHANGE_FAILED', 'X PKCE verifier is missing.');
        }
        $headers = [];
        if ($config['client_secret'] !== '') {
            $headers[] = 'Authorization: Basic ' . base64_encode($config['client_id'] . ':' . $config['client_secret']);
        }
        $response = social_http('POST', $config['token_url'], [
            'code' => $code,
            'grant_type' => 'authorization_code',
            'client_id' => $config['client_id'],
            'redirect_uri' => $redirect,
            'code_verifier' => $codeVerifier,
        ], $headers);
    }
    $accessToken = trim((string) ($response['access_token'] ?? ''));
    if ($accessToken === '' || strlen($accessToken) > 8192) {
        throw new SocialAuthException('PROVIDER_EXCHANGE_FAILED', 'Provider access token was missing.');
    }
    return $accessToken;
}

function social_fetch_identity(string $provider, string $accessToken): array
{
    $headers = ['Authorization: Bearer ' . $accessToken];
    if ($provider === 'google') {
        $profile = social_http('GET', 'https://openidconnect.googleapis.com/v1/userinfo', [], $headers);
        $id = trim((string) ($profile['sub'] ?? ''));
        $email = trim((string) ($profile['email'] ?? ''));
        $name = trim((string) ($profile['name'] ?? ''));
        $avatar = trim((string) ($profile['picture'] ?? ''));
    } elseif ($provider === 'facebook') {
        $url = 'https://graph.facebook.com/me?fields=' . rawurlencode('id,name,email,picture.type(large)');
        $profile = social_http('GET', $url, [], $headers);
        $id = trim((string) ($profile['id'] ?? ''));
        $email = trim((string) ($profile['email'] ?? ''));
        $name = trim((string) ($profile['name'] ?? ''));
        $avatar = trim((string) ($profile['picture']['data']['url'] ?? ''));
    } else {
        $profile = social_http('GET',
            'https://api.x.com/2/users/me?user.fields=id,name,username,profile_image_url', [], $headers);
        $data = is_array($profile['data'] ?? null) ? $profile['data'] : [];
        $id = trim((string) ($data['id'] ?? ''));
        $email = '';
        $name = trim((string) ($data['name'] ?? ($data['username'] ?? '')));
        $avatar = trim((string) ($data['profile_image_url'] ?? ''));
    }
    if ($id === '' || strlen($id) > 191) {
        throw new SocialAuthException('PROVIDER_IDENTITY_INVALID', 'Provider identity was invalid.');
    }
    return [
        'id' => $id,
        'email' => mb_substr($email, 0, 191),
        'name' => mb_substr($name, 0, 191),
        'avatar_url' => mb_substr($avatar, 0, 1000),
    ];
}

function social_redirect_to_app(string $returnUri, string $provider, array $params): never
{
    $scheme = strtolower((string) parse_url($returnUri, PHP_URL_SCHEME));
    $host = strtolower((string) parse_url($returnUri, PHP_URL_HOST));
    $path = (string) parse_url($returnUri, PHP_URL_PATH);
    if (!str_ends_with($scheme, '.parallaxsdk') || $host !== 'social-auth' || $path !== '/callback') {
        http_response_code(400);
        exit('INVALID_RETURN_URI');
    }
    $params = array_merge(['provider' => $provider], $params);
    $target = $returnUri . '?' . http_build_query($params, '', '&', PHP_QUERY_RFC3986);
    header('Cache-Control: no-store, max-age=0');
    header('Location: ' . $target, true, 302);
    exit;
}

function social_cleanup(mysqli $conn): void
{
    if (random_int(1, 20) !== 1) {
        return;
    }
    $conn->query('DELETE FROM social_auth_transactions WHERE expires_at < UTC_TIMESTAMP()');
    $conn->query('DELETE FROM social_auth_tickets WHERE expires_at < UTC_TIMESTAMP() OR consumed_at < UTC_TIMESTAMP() - INTERVAL 1 DAY');
    $conn->query('DELETE FROM social_auth_sessions WHERE expires_at < UTC_TIMESTAMP()');
}
