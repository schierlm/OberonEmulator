#!/bin/sh
set -e

integrate() {
	rm -rf work/*.orig
	mkdir -p source/$1
	for file in work/*; do
		name=$(basename $file)
		if [ ! -f source/$name ]; then
			mv $file source/$name
		elif ! cmp -s $file source/$name; then
			:>source/$name
			mv $file source/$1/$name
		fi
	done
}

./make_compatible_image.sh
integrate Compatible
./make_bigmem_image.sh
integrate Bigmem
./make_js_image.sh
integrate JS
./make_js_color_image.sh
integrate JSColor
./make_js_debuggable_image.sh
integrate JSDebug

./make_compatible_image.sh
integrate Compatible
./make_bigmem_image.sh
integrate Bigmem
./make_js_image.sh
integrate JS
./make_js_color_image.sh
integrate JSColor
./make_js_debuggable_image.sh
integrate JSDebug

find source -type f -a -size 0 -delete

echo "## Everything combined! ##"
