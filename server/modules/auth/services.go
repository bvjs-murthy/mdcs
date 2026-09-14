package auth

import (
	"context"
	"errors"
	"mdcs-server/data"
	"mdcs-server/tools/auth"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"golang.org/x/crypto/bcrypt"
)

func getAccessTokens(uid string) (string, string, error) {
	auth_tok, err := auth.GenAuthTok(uid)

	if err != nil {
		return "", "", errors.New("TOK_GEN_ERR")
	}

	refresh_tok, err := auth.GenRefreshTok(uid)

	if err != nil {
		return "", "", errors.New("TOK_GEN_ERR")
	}

	return auth_tok, refresh_tok, nil
}

func createUsr(usr CreateUsrReq) (string, error) {
	hashed, err := bcrypt.GenerateFromPassword(
		[]byte(usr.Password),
		bcrypt.DefaultCost,
	)

	if err != nil {
		return "", errors.New("PASSWORD_HASH_ERROR")
	}

	usr.Password = string(hashed)
	usr_id := uuid.NewString()

	_, err = data.Pool.Exec(
		context.Background(),
		`
		INSERT INTO users
			(user_id, username, email, password)
			
		VALUES
			($1, $2, $3, $4)
		`,
		usr_id,
		usr.Username,
		usr.Email,
		usr.Password,
	)

	if err != nil {

		if pg_err, ok := errors.AsType[*pgconn.PgError](err); ok {

			if pg_err.Code == "23505" {
				return "", errors.New("DUPLICATE_USER")
			}
		}

		return "", err
	}

	err = auth.SendOtp(usr_id, usr.Email)

	if err != nil {
		return "", errors.New("OTP_VER_FAIL")
	}

	return usr_id, errors.New("DB_ERROR")
}

/*
Need to implement the tries. A max of 5 tries are allowed before the OTP is erased.
*/
func verifyOtp(usr ValidateUsrReq) error {
	row, err := data.Pool.Query(
		context.Background(),
		`
		SELECT
			otp,
			sent_at
		FROM otp

		WHERE user_id = $1
		`,
		usr.UserId,
	)

	if err != nil {
		return err
	}

	var otp_hash struct {
		OTP  string
		Sent time.Time
	}

	err = row.Scan(&otp_hash.OTP, &otp_hash.Sent)

	if err != nil {
		return errors.New("OTP_NOT_FOUND")
	}

	dur := time.Since(otp_hash.Sent)

	if dur.Seconds() > 300 {
		return errors.New("OTP_EXPIRED")
	}

	err = bcrypt.CompareHashAndPassword([]byte(otp_hash.OTP), []byte(usr.OTP))

	if err != nil {
		return errors.New("INCORRECT_OTP")
	}

	_, err = data.Pool.Exec(
		context.Background(),
		`
		DELETE FROM otp
		WHERE user_id = $1
		`,
		usr.UserId,
	)

	if err != nil {
		return errors.New("DB_ERROR")
	}

	_, err = data.Pool.Exec(
		context.Background(),
		`
		UPDATE users
		SET phase = 'VERIFIED'
		
		WHERE user_id = $1
		`,
		usr.UserId,
	)

	if err != nil {
		return errors.New("DB_ERROR")
	}

	return nil
}

func firstEnroll(enr EnrollFirReq) (string, string, error) {
	transac, err := data.Pool.Begin(context.Background())

	if err != nil {
		return "", "", errors.New("DB_ERROR")
	}

	defer transac.Rollback(context.Background())

	var phase string

	err = transac.QueryRow(
		context.Background(),
		`
		SELECT phase
		FROM users
		
		WHERE user_id = $1
		`,
		enr.UserId,
	).Scan(&phase)

	if err != nil {

		if errors.Is(err, pgx.ErrNoRows) {
			return "", "", errors.New("NO_SUCH_USER")
		}

		return "", "", err
	}

	if phase != "VERIFIED" {
		return "", "", errors.New("FORBIDDEN_ACCESS")
	}

	wid := uuid.NewString()
	did := uuid.NewString()

	_, err = transac.Exec(
		context.Background(),
		`
		INSERT INTO workspaces
			(workspace_id, user_id, workspace_name, main_device)

		VALUES
			($1, $2, $3, $4)
		`,
		wid,
		enr.UserId,
		enr.WorkspaceName,
		did,
	)

	if err != nil {
		var pgErr *pgconn.PgError

		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return "", "", errors.New("DUPLICATE_WORKSPACE")
		}

		return "", "", err
	}

	_, err = transac.Exec(
		context.Background(),
		`
		INSERT INTO devices
			(device_id, workspace_id, name)

		VALUES
			($1, $2, $3)
		`,
		did,
		wid,
		enr.DeviceName,
	)

	if err != nil {
		var pgErr *pgconn.PgError

		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return "", "", errors.New("DUPLICATE_DEVICE")
		}

		return "", "", err
	}

	_, err = transac.Exec(
		context.Background(),
		`
		UPDATE users
		SET phase = $1
		
		WHERE user_id = $2
		`,
		"ONBOARDED",
		enr.UserId,
	)

	if err != nil {
		return "", "", err
	}

	if err := transac.Commit(context.Background()); err != nil {
		return "", "", err
	}

	return wid, did, nil
}

func loginVer(lin LoginReq) (error, string, string, string, string) {
	transac, err := data.Pool.Begin(context.Background())

	if err != nil {
		return errors.New("DB_ERROR"), "", "", "", ""
	}

	defer transac.Rollback(context.Background())

	var usr_id, username, email, pswd, phase string

	err = transac.QueryRow(
		context.Background(),
		`
		SELECT
			user_id, username, email, password, phase
		FROM users

		WHERE email = $1
		`,
		lin.Email,
	).Scan(&usr_id, &username, &email, &pswd, &phase)

	if err != nil {
		return errors.New("DB_ERROR"), "", "", "", ""
	}

	err = bcrypt.CompareHashAndPassword([]byte(pswd), []byte(lin.Password))

	if err != nil {
		return errors.New("INVALID_CREDS"), "", "", "", ""
	}

	return nil, usr_id, username, email, phase
}
