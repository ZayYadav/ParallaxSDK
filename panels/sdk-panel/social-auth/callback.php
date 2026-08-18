<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/conn.php';
require_once __DIR__ . '/SocialAuth.php';

header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');

$transaction = null;
try {
    if (!social_enabled()) {
        throw new SocialAuthException('SOCIAL_AUTH_DISABLED', 'Social sign in is disabled.');
    }
    $state = trim((string) ($_GET['state'] ?? ''));
    if (preg_match('/^[A-Za-z0-9_-]{40,128}$/D', $state) !== 1) {
        throw new SocialAuthException('INVALID_STATE', 'Invalid OAuth state.');
    }
    $stateHash = hash('sha256', $state);

    $conn->begin_transaction();
    try {
        $stmt = $conn->prepare(
            'SELECT id,provider,package_name,device_id,install_nonce_hash,return_uri,code_verifier,expires_at
             FROM social_auth_transactions WHERE state_hash=? LIMIT 1 FOR UPDATE'
        );
        $stmt->bind_param('s', $stateHash);
        $stmt->execute();
        $transaction = $stmt->get_result()->fetch_assoc();
        $stmt->close();
        if (!$transaction || strtotime((string) $transaction['expires_at'] . ' UTC') < time()) {
            throw new SocialAuthException('STATE_EXPIRED', 'OAuth transaction is expired.');
        }
        $id = (int) $transaction['id'];
        $stmt = $conn->prepare('DELETE FROM social_auth_transactions WHERE id=?');
        $stmt->bind_param('i', $id);
        $stmt->execute();
        $stmt->close();
        $conn->commit();
    } catch (Throwable $error) {
        $conn->rollback();
        throw $error;
    }

    $provider = (string) $transaction['provider'];
    $returnUri = (string) $transaction['return_uri'];
    $providerError = trim((string) ($_GET['error'] ?? ''));
    if ($providerError !== '') {
        panel_audit($conn, 'social_auth', 'provider_cancelled', (string) $transaction['device_id'], [
            'provider' => $provider,
            'package' => (string) $transaction['package_name'],
        ]);
        $normalized = in_array(strtolower($providerError), ['access_denied', 'user_cancelled', 'cancelled'], true)
            ? 'cancelled' : 'provider_error';
        social_redirect_to_app($returnUri, $provider, ['error' => $normalized]);
    }

    $code = trim((string) ($_GET['code'] ?? ''));
    if ($code === '' || strlen($code) > 8192) {
        social_redirect_to_app($returnUri, $provider, ['error' => 'missing_code']);
    }

    $accessToken = social_exchange_code(
        $provider,
        $code,
        isset($transaction['code_verifier']) ? (string) $transaction['code_verifier'] : null
    );
    $identity = social_fetch_identity($provider, $accessToken);

    $providerUserId = (string) $identity['id'];
    $email = (string) $identity['email'];
    $name = (string) $identity['name'];
    $avatar = (string) $identity['avatar_url'];
    $stmt = $conn->prepare(
        'INSERT INTO social_auth_users (provider,provider_user_id,email,display_name,avatar_url,last_login_at)
         VALUES (?,?,?,?,?,UTC_TIMESTAMP())
         ON DUPLICATE KEY UPDATE
            email=VALUES(email),display_name=VALUES(display_name),avatar_url=VALUES(avatar_url),
            last_login_at=UTC_TIMESTAMP(),id=LAST_INSERT_ID(id)'
    );
    if (!$stmt) {
        throw new SocialAuthException('SERVER_DATABASE_ERROR', 'Unable to save social identity.');
    }
    $stmt->bind_param('sssss', $provider, $providerUserId, $email, $name, $avatar);
    if (!$stmt->execute()) {
        $stmt->close();
        throw new SocialAuthException('SERVER_DATABASE_ERROR', 'Unable to save social identity.');
    }
    $userId = (int) $conn->insert_id;
    $stmt->close();
    if ($userId < 1) {
        $stmt = $conn->prepare('SELECT id FROM social_auth_users WHERE provider=? AND provider_user_id=? LIMIT 1');
        $stmt->bind_param('ss', $provider, $providerUserId);
        $stmt->execute();
        $userId = (int) ($stmt->get_result()->fetch_assoc()['id'] ?? 0);
        $stmt->close();
    }
    if ($userId < 1) {
        throw new SocialAuthException('SERVER_DATABASE_ERROR', 'Unable to resolve social identity.');
    }

    $ticket = social_random_token(32);
    $ticketHash = hash('sha256', $ticket);
    $packageName = (string) $transaction['package_name'];
    $deviceId = (string) $transaction['device_id'];
    $installNonceHash = (string) $transaction['install_nonce_hash'];
    $ticketSeconds = max(60, min(600, (int) panel_config('SOCIAL_AUTH_TICKET_SECONDS', 120)));
    $ticketExpiry = gmdate('Y-m-d H:i:s', time() + $ticketSeconds);
    $stmt = $conn->prepare(
        'INSERT INTO social_auth_tickets
            (ticket_hash,user_id,provider,package_name,device_id,install_nonce_hash,expires_at)
         VALUES (?,?,?,?,?,?,?)'
    );
    $stmt->bind_param(
        'sisssss',
        $ticketHash,
        $userId,
        $provider,
        $packageName,
        $deviceId,
        $installNonceHash,
        $ticketExpiry
    );
    $stmt->execute();
    $stmt->close();

    panel_audit($conn, 'social_auth', 'provider_success', $deviceId, [
        'provider' => $provider,
        'package' => $packageName,
    ]);
    social_cleanup($conn);
    social_redirect_to_app($returnUri, $provider, ['ticket' => $ticket]);
} catch (SocialAuthException $error) {
    error_log('Social OAuth callback: ' . $error->authCode . ' - ' . $error->getMessage());
    if (is_array($transaction) && !empty($transaction['return_uri']) && !empty($transaction['provider'])) {
        social_redirect_to_app(
            (string) $transaction['return_uri'],
            (string) $transaction['provider'],
            ['error' => 'provider_exchange_failed']
        );
    }
    http_response_code(400);
    header('Content-Type: text/plain; charset=UTF-8');
    echo 'Sign in could not be completed. Return to the app and try again.';
} catch (Throwable $error) {
    error_log('Social OAuth callback server error: ' . $error->getMessage());
    if (is_array($transaction) && !empty($transaction['return_uri']) && !empty($transaction['provider'])) {
        social_redirect_to_app(
            (string) $transaction['return_uri'],
            (string) $transaction['provider'],
            ['error' => 'server_error']
        );
    }
    http_response_code(500);
    header('Content-Type: text/plain; charset=UTF-8');
    echo 'Sign in server error. Return to the app and try again.';
}
