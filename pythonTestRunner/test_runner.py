import os.path
import subprocess
import sys

runner_dir = "pythonTestRunner"
test_cache_file = f"{runner_dir}/previous_test.txt"


def run_test(name: str):
    subprocess.call(["run_test.bat", name],
                    stdin=subprocess.PIPE,
                    universal_newlines=True,
                    shell=True,
                    bufsize=0)


def cache_current_test_name(name: str):
    f = open(test_cache_file, "w")
    f.write(name)
    f.close()


def get_current_cached_test_name() -> str:
    if os.path.exists(test_cache_file):
        f = open(test_cache_file, "r")
        contents = f.read()
        f.close()
        return contents
    else:
        return ""


if __name__ == "__main__":
    args = sys.argv

    if len(args) < 2:
        print("Not enough arguments passed, expected 2, "
              f"the second arg should be the path to the file where the test resides {args}")
        exit()
    else:
        os_safe_path = args[1].replace("\\", "/")
        class_name = os_safe_path.replace("/", ".") \
            .replace(".scala", "")  # could substring but lazy

        # this will allow us to create a test, run it using external tool and keep running it
        # while doing implementation without having to switch about.
        # We are tied to using run action (ctrl/cmd + shift + a) to do it but run action  remembers the last call
        # so not as cack-handed as could be
        if class_name.endswith("Spec") or class_name.endswith("Test"):
            cache_current_test_name(class_name)
            run_test(class_name)
        else:
            cached_test_name = get_current_cached_test_name()
            print(f"Running test {cached_test_name}")
            if cached_test_name != "":
                run_test(cached_test_name)
