#include <stdio.h>
#include <stdlib.h>

#include "router.h"

int main()
{
    printf("Hello world!\n");
    router_ctx_t* ctx = router_init(NULL);
    router_deinit(ctx);
    return 0;
}
