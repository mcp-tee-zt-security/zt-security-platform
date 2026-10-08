#!/usr/bin/env python3
from base64 import b64encode
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization

key = Ed25519PrivateKey.generate()
private = key.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
public = key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
print('ZT_POLICY_SIGNING_PRIVATE_KEY_B64=' + b64encode(private).decode())
print('ZT_POLICY_PUBLIC_KEY_B64=' + b64encode(public).decode())
print('Never commit the private key. Store it in AWS Secrets Manager/KMS-backed secret storage in production.')
