python setup.py build_ext --inplace --quiet --rpath /Users/caberger/Documents/Research/code/eh_python/libs $1 
mv *.so ../queries/$1.so
