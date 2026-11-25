package com.ahu.ahutong.data.crawler.model.ycard

class BathroomRequest(
    bathroom: String,
    amount: String,
    thirdPartyJson: String
) : RequestBody() {

    init {
        var feeitemid :String? = null
        when(bathroom){
            "大兴浴室"->{
                feeitemid = "409" // TODO(民政职业大学): 缴费项目 ID 待核对
            }
            "燕郊浴室"->{
                feeitemid = "430" // TODO(民政职业大学): 缴费项目 ID 待核对
            }
            else -> {
                throw IllegalArgumentException("Unknown bathroom.")
            }
        }

        feeitemid.let{
            addParams(
                mapOf(
                    "feeitemid" to it,
                    "tranamt" to amount,
                    "flag" to "choose",
                    "source" to "app",
                    "paystep" to "0",
                    "abstracts" to "",
                    "third_party" to thirdPartyJson
                )
            )
        }


    }
}