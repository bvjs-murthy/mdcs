package version

import (
	"fmt"
	"strconv"
	"strings"
)

// Semver data
type SemVer struct {
	Major int
	Minor int
	Patch int
}

func Parse(version string) (SemVer, error) {
	parts := strings.Split(version, ".")

	if len(parts) < 3 {
		return SemVer{}, fmt.Errorf("Invalid version format: %s", version)
	}

	major, err := strconv.Atoi(parts[0])
	if err != nil {
		return SemVer{}, err
	}

	minor, err := strconv.Atoi(parts[1])
	if err != nil {
		return SemVer{}, err
	}

	patch, err := strconv.Atoi(parts[2])
	if err != nil {
		return SemVer{}, err
	}

	return SemVer{Major: major, Minor: minor, Patch: patch}, nil
}

func Lower(curr, min SemVer) bool {

	if curr.Major != min.Major {
		return curr.Major < min.Major
	}

	if curr.Minor != min.Minor {
		return curr.Minor < min.Minor
	}

	return curr.Patch < min.Patch
}

func Inrange(curr, min, max SemVer) bool {
	isAboveMin := !Lower(curr, min)
	isBelowMax := !Lower(max, curr)

	return isAboveMin && isBelowMax
}
