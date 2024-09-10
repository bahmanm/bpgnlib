SHELL := /usr/bin/env bash
.DEFAULT_GOAL := test

####################################################################################################

export ROOT := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))

####################################################################################################

.PHONY : bmakelib/bmakelib.mk
include  bmakelib/bmakelib.mk

####################################################################################################

gradle.gradle ?= ./gradlew
gradle.options ?= --console plain
gradle.command = $(gradle.gradle) $(gradle.options)

####################################################################################################

.PHONY : gradle-options(%)

gradle.options(%) :
	$(eval gradle.options += $(*))

####################################################################################################

.PHONY : gradle(%)

gradle(%) :
	$(gradle.command) $(*)

####################################################################################################

.PHONY : test

test : gradle( check )

####################################################################################################

.PHONY : format

format : gradle( spotlessApply )

####################################################################################################

.PHONY : compile

compile : gradle( classes )

####################################################################################################

.PHONY : clean

clean : gradle( clean )
clean:
	-@rm -rf build lib/build