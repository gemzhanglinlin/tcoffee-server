cd /var/vhosts/tcoffee/
sudo -u tcoffee sh -c 'source /mnt/common/etc/profile.d/vitalit_lsf.sh;
                       source /etc/profile.d/vitalit.sh;
                       module add SequenceAnalysis/MultipleSequenceAlignment/T-Coffee/11.00.8cbe486;
                       module add SequenceAnalysis/ProtoGene/4.2.0;
                       /var/vhosts/tcoffee/play/play start ./tserver --%vital'
cd - >/dev/null
