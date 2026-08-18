<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/conn.php';
require_once __DIR__ . '/SocialAuth.php';

header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    header('Allow: POST');
    social_json_send(['status' => 'fail', 'code' => 'METHOD_NOT_ALLOWED', 'message' => 'POST required.'], 405);
}

try {
    if (!social_enabled()) {
        throw new SocialAuthException('SOCIAL_AUTH_DISABLED', 'Social sign in is disabled.');
    }
    if (!panel_rate_limit(
        $conn,
        'social-auth|' . panel_client_ip(),
        max(10, (int) panel_config('RATE_LIMIT_PER_MINUTE', 30))
    )) {
        social_json_send(['status' => 'fail', 'code' => 'RATE_LIMITED', 'message' => 'Too many requests.'], 429);
    }

    $payload = social_json_input();
    $action = strtolower(trim((string) ($payload['action'] ?? '')));
    [$packageName, $deviceId, $installNonce] = social_validate_client_identity($payload);
    $installNonceHash = hash('sha256', $installNonce);

    $allowedPackages = panel_config('ALLOWED_PACKAGES', []);
    if (is_array($allowedPackages) && $allowedPackages !== []
        && !in_array($packageName, $allowedPackages, true)) {
        throw new SocialAuthException('PACKAGE_NOT_ALLOWED', 'This package is not allowed to use social sign in.');
    }

    if ($action === 'start') {
        $provider = strtolower(trim((string) ($payload['provider'] ?? '')));
        social_provider_config($provider);
        $returnUri = trim((string) ($payload['return_uri'] ?? ''));
        $expectedReturnUri = social_expected_return_uri($packageName);
        if ($returnUri === '' || !hash_equals($expectedReturnUri, strtolower($returnUri))) {
            throw new SocialAuthException('INVALID_RETURN_URI', 'SDK callback URI does not match this package.');
        }

        $state = social_random_token(32);
        $stateHash = hash('sha256', $state);
        $codeVerifier = $provider === 'x' ? social_random_token(64) : null;
        $stmt = $conn->prepare(
            'INSERT INTO social_auth_transactions
                (state_hash,provider,package_name,device_id,install_nonce_hash,return_uri,code_verifier,expires_at)
             VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)'
        );
        if (!$stmt) {
            throw new SocialAuthException('SERVER_DATABASE_ERROR', 'Unable to create sign-in transaction.');
        }
        $stmt->bind_param(
            'sssssss',
            $stateHash,
            $provider,
            $packageName,
            $deviceId,
            $installNonceHash,
            $returnUri,
            $codeVerifier
        );
        if (!$stmt->execute()) {
            $stmt->close();
            throw new SocialAuthException('SERVER_DATABASE_ERROR', 'Unable to create sign-in transaction.');
        }
        $stmt->close();
        social_cleanup($conn);
        social_json_send([
            'status' => 'success',
            'authorization_url' => social_build_authorization_url($provider, $state, $codeVerifier),
            'expires_in' => 600,
        ]);
    }

    if ($action === 'complete') {
        $ticket = trim((string) ($payload['ticket'] ?? ''));
        if (preg_match('/^[A-Za-z0-9_-]{40,128}$/D', $ticket) !== 1) {
            throw new SocialAuthException('INVALID_TICKET', 'Invalid sign-in ticket.');
        }
        $ticketHash = hash('sha256', $ticket);
        $requestedProvider = strtolower(trim((string) ($payload['provider'] ?? '')));

        $conn->begin_transaction();
        try {
            $stmt = $conn->prepare(
                'SELECT t.provider,t.package_name,t.device_id,t.install_nonce_hash,t.expires_at,t.consumed_at,
                        u.id AS user_id,u.provider_user_id,u.email,u.display_name,u.avatar_url
                 FROM social_auth_tickets t
                 JOIN social_auth_users u ON u.id=t.user_id
                 WHERE t.ticket_hash=? LIMIT 1 FOR UPDATE'
            );
            $stmt->bind_param('s', $ticketHash);
            $stmt->execute();
            $row = $stmt->get_result()->fetch_assoc();
            $stmt->close();
            if (!$row
                || $row['consumed_at'] !== null
                || strtotime((string) $row['expires_at'] . ' UTC') < time()
                || !hash_equals((string) $row['package_name'], $packageName)
                || !hash_equals((string) $row['device_id'], $deviceId)
                || !hash_equals((string) $row['install_nonce_hash'], $installNonceHash)
                || ($requestedProvider !== '' && !hash_equals((string) $row['provider'], $requestedProvider))) {
                throw new SocialAuthException('TICKET_REJECTED', 'Sign-in ticket is expired or does not belong to this SDK install.');
            }

            $stmt = $conn->prepare('UPDATE social_auth_tickets SET consumed_at=UTC_TIMESTAMP() WHERE ticket_hash=?');
            $stmt->bind_param('s', $ticketHash);
            $stmt->execute();
            $stmt->close();

            $sessionToken = social_random_token(32);
            $sessionHash = hash('sha256', $sessionToken);
            $sessionSeconds = max(3600, min(7776000, (int) panel_config('SOCIAL_AUTH_SESSION_SECONDS', 2592000)));
            $expiresAt = time() + $sessionSeconds;
            $expiresSql = gmdate('Y-m-d H:i:s', $expiresAt);
            $userId = (int) $row['user_id'];
            $stmt = $conn->prepare(
                'INSERT INTO social_auth_sessions
                    (session_hash,user_id,package_name,device_id,install_nonce_hash,expires_at)
                 VALUES (?,?,?,?,?,?)'
            );
            $stmt->bind_param(
                'sissss',
                $sessionHash,
                $userId,
                $packageName,
                $deviceId,
                $installNonceHash,
                $expiresSql
            );
            $stmt->execute();
            $stmt->close();
            $conn->commit();

            panel_audit($conn, 'social_auth', 'login_success', $deviceId, [
                'provider' => (string) $row['provider'],
                'package' => $packageName,
            ]);
            social_json_send([
                'status' => 'success',
                'session_token' => $sessionToken,
                'session_expires' => $expiresAt,
                'user' => [
                    'provider' => (string) $row['provider'],
                    'id' => (string) $row['provider_user_id'],
                    'email' => (string) ($row['email'] ?? ''),
                    'name' => (string) ($row['display_name'] ?? ''),
                    'avatar_url' => (string) ($row['avatar_url'] ?? ''),
                ],
            ]);
        } catch (Throwable $error) {
            if ($conn->errno === 0 && $conn->in_transaction) {
                $conn->rollback();
            } elseif ($conn->in_transaction) {
                $conn->rollback();
            }
            throw $error;
        }
    }

    if ($action === 'session') {
        $sessionToken = trim((string) ($payload['session_token'] ?? ''));
        if (preg_match('/^[A-Za-z0-9_-]{40,128}$/D', $sessionToken) !== 1) {
            throw new SocialAuthException('SESSION_INVALID', 'Invalid social session.');
        }
        $sessionHash = hash('sha256', $sessionToken);
        $stmt = $conn->prepare(
            'SELECT s.expires_at,s.package_name,s.device_id,s.install_nonce_hash,
                    u.provider,u.provider_user_id,u.email,u.display_name,u.avatar_url
             FROM social_auth_sessions s
             JOIN social_auth_users u ON u.id=s.user_id
             WHERE s.session_hash=? AND s.expires_at>UTC_TIMESTAMP() LIMIT 1'
        );
        $stmt->bind_param('s', $sessionHash);
        $stmt->execute();
        $row = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$row
            || !hash_equals((string) $row['package_name'], $packageName)
            || !hash_equals((string) $row['device_id'], $deviceId)
            || !hash_equals((string) $row['install_nonce_hash'], $installNonceHash)) {
            throw new SocialAuthException('SESSION_INVALID', 'Social session is expired or belongs to another SDK install.');
        }
        $stmt = $conn->prepare('UPDATE social_auth_sessions SET last_seen=UTC_TIMESTAMP() WHERE session_hash=?');
        $stmt->bind_param('s', $sessionHash);
        $stmt->execute();
        $stmt->close();
        social_cleanup($conn);
        social_json_send([
            'status' => 'success',
            'session_token' => $sessionToken,
            'session_expires' => strtotime((string) $row['expires_at'] . ' UTC'),
            'user' => [
                'provider' => (string) $row['provider'],
                'id' => (string) $row['provider_user_id'],
                'email' => (string) ($row['email'] ?? ''),
                'name' => (string) ($row['display_name'] ?? ''),
                'avatar_url' => (string) ($row['avatar_url'] ?? ''),
            ],
        ]);
    }

    if ($action === 'logout') {
        $sessionToken = trim((string) ($payload['session_token'] ?? ''));
        if (preg_match('/^[A-Za-z0-9_-]{40,128}$/D', $sessionToken) === 1) {
            $sessionHash = hash('sha256', $sessionToken);
            $stmt = $conn->prepare(
                'DELETE FROM social_auth_sessions
                 WHERE session_hash=? AND package_name=? AND device_id=? AND install_nonce_hash=?'
            );
            $stmt->bind_param('ssss', $sessionHash, $packageName, $deviceId, $installNonceHash);
            $stmt->execute();
            $stmt->close();
        }
        social_json_send(['status' => 'success']);
    }

    throw new SocialAuthException('INVALID_ACTION', 'Unknown social auth action.');
} catch (SocialAuthException $error) {
    panel_audit($conn, 'social_auth', strtolower($error->authCode), $deviceId ?? '', [
        'package' => $packageName ?? '',
    ]);
    $status = in_array($error->authCode, ['RATE_LIMITED'], true) ? 429
        : (str_contains($error->authCode, 'SERVER_') ? 500 : 400);
    social_json_send([
        'status' => 'fail',
        'code' => $error->authCode,
        'message' => $error->getMessage(),
    ], $status);
} catch (Throwable $error) {
    error_log('Social auth API error: ' . $error->getMessage());
    panel_audit($conn, 'social_auth', 'server_error', $deviceId ?? '', [
        'package' => $packageName ?? '',
    ]);
    social_json_send([
        'status' => 'fail',
        'code' => 'SERVER_ERROR',
        'message' => 'Social sign in could not be completed.',
    ], 500);
}
