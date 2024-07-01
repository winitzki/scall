logfile="$1"
jarfile="$2"
targetjar="$3"
tmp=${TMPDIR:-/tmp}/leanjar$$

rm -rf "$tmp"
mkdir -p "$tmp"

egrep '(\[Loaded [A-Za-z_]|class,load)' "$logfile" | grep -v zulujdk | sed -e 's/.*\(Loaded \|class,load[ ]]*\)\([A-Za-z_][^ $]*\).*/\2/; s|\.|/|g; ' | sort | uniq > "$tmp"/class_file_list.txt

cp "$jarfile" "$tmp"/
b="$(basename "$jarfile")"

(
	cd "$tmp"
	tar -xvf "$b" \*.conf
	jar -xvf "$b" META-INF
	jar -xf "$b" @class_file_list.txt
	rm class_file_list.txt "$b"
	jar cf targetfile-jar.jar .
)

cp "$tmp"/targetfile-jar.jar "$targetjar"

rm -rf "$tmp"
