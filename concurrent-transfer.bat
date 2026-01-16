@echo off
start cmd /k mvn exec:java "-Dexec.mainClass=uk.ac.westminster.ds.Main" "-Dexec.args=client transfer alice alice2 30"
start cmd /k mvn exec:java "-Dexec.mainClass=uk.ac.westminster.ds.Main" "-Dexec.args=client transfer alice alice2 30"
