package auth

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"math/big"
	"mdcs-server/data"
	"mdcs-server/tools"
	"os"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"golang.org/x/crypto/bcrypt"
)

func SendOtp(user_id, email string) error {
	n, err := rand.Int(rand.Reader, big.NewInt(900000))

	if err != nil {
		return err
	}

	otp := fmt.Sprintf("%06d", n.Int64()+100000)

	otp_hash, err := bcrypt.GenerateFromPassword(
		[]byte(otp),
		bcrypt.DefaultCost,
	)

	msg := fmt.Sprintf(
		"From: %s\r\n"+
			"To: %s\r\n"+
			"Subject: MDCS Verification\r\n"+
			"MIME-version: 1.0;\r\n"+
			"Content-Type: text/html; charset=\"UTF-8\";\r\n\r\n"+

			`
		<html>
			<body>
				<h2>MDCS Verification</h2>

				<p>Hi! Thanks for downloading MDCS</p>

				<p>
					If you have any queries or feedback, feel free to reply to this
					email.
				</p>
				
				<p>Your OTP is:</p>

				<h1>%s</h1>

				<p>This code expires in 5 minutes.</p>
			</body>
		</html>
		`,
		os.Getenv("EMAIL"), email, otp,
	)

	err = tools.SendEmail(email, []byte(msg))

	if err != nil {
		return err
	}

	_, err = data.Pool.Exec(
		context.Background(),
		`INSERT INTO otp
			(user_id, otp, sent_at)
			
		VALUES
			($1, $2, NOW())`,
		user_id,
		string(otp_hash),
	)

	if err != nil {

		if pg_err, ok := errors.AsType[*pgconn.PgError](err); ok {

			if pg_err.Code == "23505" {

				_, err = data.Pool.Exec(
					context.Background(),
					`
					UPDATE otp
					SET otp = $1, sent_at = NOW()
						
					WHERE user_id = $2
					`,
					string(otp_hash),
					user_id,
				)
			}
		}
	}

	return nil
}

func GenAuthTok(uid string) (string, error) {
	claims := jwt.MapClaims{
		"user_id": uid,
		"exp":     time.Now().Add(60 * time.Minute).Unix(),
		"iat":     time.Now().Unix(),
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)

	return token.SignedString([]byte(os.Getenv("JWT_KEY")))
}

func GenRefreshTok(uid string) (string, error) {
	key := make([]byte, 32)
	_, err := rand.Read(key)

	if err != nil {
		return "", err
	}

	token := base64.URLEncoding.EncodeToString(key)
	hashed, err := bcrypt.GenerateFromPassword(
		[]byte(token),
		bcrypt.DefaultCost,
	)

	if err != nil {
		return "", err
	}

	_, err = data.Pool.Exec(
		context.Background(),
		`
		INSERT INTO auth_tokens
			(user_id, token, created_at)

		VALUES
			($1, $2, NOW())
		`,
		uid,
		string(hashed),
	)

	return token, err
}
