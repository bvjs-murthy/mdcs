package tools

import (
	"net/smtp"
	"os"
)

func SendEmail(to string, msg []byte) error {
	from := os.Getenv("EMAIL")
	pswd := os.Getenv("EMAIL_PSWD")

	host := "smtp.gmail.com"
	port := "587"

	auth := smtp.PlainAuth("", from, pswd, host)

	err := smtp.SendMail(
		host+":"+port,
		auth,
		from,
		[]string{to},
		msg,
	)

	return err
}
