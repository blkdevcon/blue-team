# Linux Security and Monitoring Scripts

These are a collection of security and monitoring scripts you can use to monitor your Linux installation
for security-related events or for an investigation. Each script works on its own and is independent of other scripts.
The scripts can be set up to either print out their results, send them to you via mail,
or using [AlertR](https://github.com/sqall01/alertR) as notification channel.

## Repository Structure

The scripts are located in the directory `scripts/`.
Each script contains a short summary in the header of the file with a description of what it is supposed to do,
(if needed) dependencies that have to be installed and (if available) references to where the idea for
this script stems from.

Each script has a configuration file in the `scripts/config/` directory to configure it.
If the configuration file was not found during the execution of the script,
the script will fall back to default settings and print out the results.
Hence, it is not necessary to provide a configuration file.

The `scripts/lib/` directory contains code that is shared between different scripts.

Scripts using a `monitor_` prefix hold a state and are only useful for monitoring purposes.
A single usage of them for an investigation will only result in showing the current state the
Linux system and not changes that might be relevant for the system's security. If you want to
establish the current state of your system as benign for these scripts, you can provide the `--init` argument.

## Usage

Take a look at the header of the script you want to execute. It contains a short description what this script
is supposed to do and what requirements are needed (if any needed at all). If requirements are needed,
install them before running the script.

The shared configuration file `scripts/config/config.py` contains settings that are used by all scripts.
Furthermore, each script can be configured by using the corresponding configuration file in the `scripts/config/`
directory. If no configuration file was found, a default setting is used and the results are printed out.

Finally, you can run all configured scripts by executing `start_search.py` (which is located in the main directory)
or by executing each script manually. A Python3 interpreter is needed to run the scripts.

### Monitoring

If you want to use the scripts to monitor your Linux system constantly, you have to perform the following steps:

1. Set up a notification channel that is supported by the scripts (currently printing out, mail,
or [AlertR](https://github.com/sqall01/alertR)).

2. Configure the scripts that you want to run using the configuration files in the `scripts/config/` directory.

3. Execute `start_search.py` with the `--init` argument to initialize the scripts with the `monitor_` prefix and let 
them establish a state of your system. However, this assumes that your system is currently uncompromised.
If you are unsure of this, you should verify its current state.

4. Set up a cron job as `root` user that executes `start_search.py`
(e.g., `0 *    * * *   root    /opt/LSMS/start_search.py` to start the search hourly).

## List of Scripts

| Name                                                                 | Script                                                                       |
|----------------------------------------------------------------------|------------------------------------------------------------------------------|
| Monitoring cron files                                                | [monitor_cron.py](scripts/monitor_cron.py)                                   |
| Monitoring /etc/hosts file                                           | [monitor_hosts_file.py](scripts/monitor_hosts_file.py)                       |
| Monitoring /etc/ld.so.preload file                                   | [monitor_ld_preload.py](scripts/monitor_ld_preload.py)                       |
| Monitoring /etc/passwd file                                          | [monitor_passwd.py](scripts/monitor_passwd.py)                               |
| Monitoring modules                                                   | [monitor_modules.py](scripts/monitor_modules.py)                             |
| Monitoring SSH authorized_keys files                                 | [monitor_ssh_authorized_keys.py](scripts/monitor_ssh_authorized_keys.py)     |
| Monitoring systemd unit files                                        | [monitor_systemd_units.py](scripts/monitor_systemd_units.py)                 |
| Search executables in /dev/shm                                       | [search_dev_shm.py](scripts/search_dev_shm.py)                               |
| Search fileless programs (memfd_create)                              | [search_memfd_create.py](scripts/search_memfd_create.py)                     |
| Search hidden ELF files                                              | [search_hidden_exe.py](scripts/search_hidden_exe.py)                         |
| Search immutable files                                               | [search_immutable_files.py](scripts/search_immutable_files.py)               |
| Search kernel thread impersonations                                  | [search_non_kthreads.py](scripts/search_non_kthreads.py)                     |
| Search processes that were started by a now disconnected SSH session | [search_ssh_leftover_processes.py](scripts/search_ssh_leftover_processes.py) |
| Search running deleted programs                                      | [search_deleted_exe.py](scripts/search_deleted_exe.py)                       |
| Test script to check if alerting works                               | [test_alert.py](scripts/test_alert.py)                                       |
| Verify integrity of installed .deb packages                          | [verify_deb_packages.py](scripts/verify_deb_packages.py)                     |

