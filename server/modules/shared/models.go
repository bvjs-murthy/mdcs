package shared

type Response struct {
	Status  bool              `json:"status"`
	Body    map[string]string `json:"body"`
	Error   string            `json:"error"`
	Message string            `json:"message"`
}
