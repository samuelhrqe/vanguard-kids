idf_component_register(SRCS "main.c"
                    INCLUDE_DIRS "."
                    REQUIRES adc_manager freertos mqtt_manager time_manager)
