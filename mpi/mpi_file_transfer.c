#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHUNK_SIZE 65536

static void usage(int rank) {
    if (rank == 0) {
        fprintf(stderr, "Usage: mpirun -np 2 ./mpi_file_transfer <input_file> [output_path_on_receiver]\n");
        fprintf(stderr, "Rank 0 sends the file; rank 1 writes it. Extra ranks (if any) exit.\n");
    }
}

static long long file_size(FILE *f) {
    if (fseek(f, 0, SEEK_END) != 0) return -1;
    long long size = ftell(f);
    if (size < 0) return -1;
    rewind(f);
    return size;
}

int main(int argc, char **argv) {
    MPI_Init(&argc, &argv);
    int rank, world;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &world);

    if (world < 2) {
        if (rank == 0) {
            fprintf(stderr, "Need at least 2 processes (sender + receiver)\n");
        }
        MPI_Finalize();
        return 1;
    }

    if (rank > 1) {
        MPI_Finalize();
        return 0;
    }

    if (argc < 2) {
        usage(rank);
        MPI_Finalize();
        return 1;
    }

    if (rank == 0) {
        const char *input_path = argv[1];
        const char *remote_name = NULL;
        if (argc >= 3) {
            remote_name = argv[2];
        } else {
            const char *slash = strrchr(input_path, '/');
            const char *bslash = strrchr(input_path, '\\');
            const char *base = input_path;
            if (slash && slash > base) base = slash + 1;
            if (bslash && bslash > base) base = bslash + 1;
            remote_name = base;
        }

        FILE *f = fopen(input_path, "rb");
        if (!f) {
            fprintf(stderr, "[sender] Could not open %s\n", input_path);
            MPI_Abort(MPI_COMM_WORLD, 2);
        }
        long long size = file_size(f);
        if (size < 0) {
            fprintf(stderr, "[sender] Could not stat %s\n", input_path);
            fclose(f);
            MPI_Abort(MPI_COMM_WORLD, 3);
        }

        int name_len = (int)strlen(remote_name);
        printf("[sender] Sending '%s' (%lld bytes) to rank 1\n", remote_name, size);

        MPI_Send(&name_len, 1, MPI_INT, 1, 0, MPI_COMM_WORLD);
        MPI_Send(&size, 1, MPI_LONG_LONG, 1, 0, MPI_COMM_WORLD);
        MPI_Send(remote_name, name_len, MPI_CHAR, 1, 0, MPI_COMM_WORLD);

        char *buffer = (char *)malloc(CHUNK_SIZE);
        if (!buffer) {
            fprintf(stderr, "[sender] Out of memory\n");
            fclose(f);
            MPI_Abort(MPI_COMM_WORLD, 4);
        }

        long long sent = 0;
        while (sent < size) {
            size_t to_read = (size_t)((size - sent) < CHUNK_SIZE ? (size - sent) : CHUNK_SIZE);
            size_t read = fread(buffer, 1, to_read, f);
            if (read == 0) {
                fprintf(stderr, "[sender] Unexpected EOF at %lld/%lld\n", sent, size);
                free(buffer);
                fclose(f);
                MPI_Abort(MPI_COMM_WORLD, 5);
            }
            MPI_Send(buffer, (int)read, MPI_CHAR, 1, 0, MPI_COMM_WORLD);
            sent += read;
        }

        printf("[sender] Done. Total sent: %lld bytes\n", sent);
        free(buffer);
        fclose(f);
    } else if (rank == 1) {
        int name_len = 0;
        long long size = 0;
        MPI_Recv(&name_len, 1, MPI_INT, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
        MPI_Recv(&size, 1, MPI_LONG_LONG, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);

        char *name = (char *)malloc((size_t)name_len + 1);
        if (!name) {
            fprintf(stderr, "[receiver] Out of memory for filename\n");
            MPI_Abort(MPI_COMM_WORLD, 6);
        }
        MPI_Recv(name, name_len, MPI_CHAR, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
        name[name_len] = '\0';

        const char *output_path = (argc >= 3) ? argv[2] : name;
        FILE *out = fopen(output_path, "wb");
        if (!out) {
            fprintf(stderr, "[receiver] Cannot open %s for write\n", output_path);
            free(name);
            MPI_Abort(MPI_COMM_WORLD, 7);
        }

        printf("[receiver] Receiving '%s' (%lld bytes) -> %s\n", name, size, output_path);

        char *buffer = (char *)malloc(CHUNK_SIZE);
        if (!buffer) {
            fprintf(stderr, "[receiver] Out of memory for buffer\n");
            free(name);
            fclose(out);
            MPI_Abort(MPI_COMM_WORLD, 8);
        }

        long long received = 0;
        while (received < size) {
            int chunk = (int)((size - received) < CHUNK_SIZE ? (size - received) : CHUNK_SIZE);
            MPI_Recv(buffer, chunk, MPI_CHAR, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
            size_t written = fwrite(buffer, 1, (size_t)chunk, out);
            if (written != (size_t)chunk) {
                fprintf(stderr, "[receiver] Write error at %lld/%lld\n", received, size);
                free(buffer);
                free(name);
                fclose(out);
                MPI_Abort(MPI_COMM_WORLD, 9);
            }
            received += chunk;
        }

        printf("[receiver] Done. Stored %s (%lld bytes)\n", output_path, received);
        free(buffer);
        free(name);
        fclose(out);
    }

    MPI_Finalize();
    return 0;
}
